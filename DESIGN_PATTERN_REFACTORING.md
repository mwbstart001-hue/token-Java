# Token 服务设计模式重构文档

## 1. 重构背景

### 1.1 重构前的问题

在重构之前，`TokenService` 承担了过多的职责：

```
TokenService (单一类)
├── JWT 生成逻辑 (buildJwt)
├── JWT 验证逻辑 (parseAndVerifyJwt)
├── Token 存储操作 (tokenStore)
├── 业务逻辑判断 (过期时间计算、状态检查)
└── 并发安全处理
```

**问题分析**：

1. **职责过多**：一个类包含了多种不同类型的逻辑
2. **难以测试**：所有逻辑耦合在一起，单元测试困难
3. **难以扩展**：添加新的操作类型（如刷新 Token）需要修改多个地方
4. **并发问题**：`invalidateToken` 存在"检查后更新"的竞态条件

### 1.2 重构目标

使用设计模式重构，实现：

1. **单一职责**：每个类只负责一件事
2. **开闭原则**：对扩展开放，对修改关闭
3. **依赖倒置**：高层模块不依赖低层模块
4. **保持 API 兼容**：不改变 `TokenService` 的公开方法签名
5. **修复并发问题**：解决 `invalidateToken` 的竞态条件

---

## 2. 设计模式应用

### 2.1 重构后的架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              调用层 (Controller/Test)                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                        TokenService (API 兼容层)                         │  │
│  │  - 保持原有方法签名不变                                                   │  │
│  │  - 业务逻辑委托给调度器                                                   │  │
│  │  - 只负责日志和参数校验                                                   │  │
│  └────────────────────────────┬────────────────────────────────────────────┘  │
└───────────────────────────────┼──────────────────────────────────────────────┘
                                │ 委托
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         调度器层 (Dispatcher Pattern)                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                   TokenOperationDispatcher                               │  │
│  │  - 统一入口：协调工厂、命令、策略的协作                                   │  │
│  │  - 提供简洁的 API：generateToken/validateToken/renewToken/invalidateToken│  │
│  │  - 负责参数校验和异常处理                                                 │  │
│  └────────────────────────────┬────────────────────────────────────────────┘  │
└───────────────────────────────┼──────────────────────────────────────────────┘
                                │
          ┌─────────────────────┼─────────────────────┐
          ▼                     ▼                     ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│   工厂模式       │   │   命令模式       │   │   策略模式       │
│(Factory Pattern)│   │(Command Pattern)│   │(Strategy Pattern)│
├─────────────────┤   ├─────────────────┤   ├─────────────────┤
│ TokenOperation  │   │  TokenCommand   │   │ TokenGenerator  │
│    Factory      │   │                 │   │ TokenValidator  │
│                 │   │ ┌─────────────┐ │   │                 │
│ 根据操作类型     │   │ │GenerateCmd  │ │   │ ┌─────────────┐ │
│ 创建对应命令对象 │   │ │ValidateCmd  │ │   │ │JwtTokenGen  │ │
│                 │   │ │RenewCmd     │ │   │ │JwtTokenValid│ │
│ 封装对象创建逻辑 │   │ │InvalidateCmd│ │   │ └─────────────┘ │
│ 解耦调用者和实现 │   │ └─────────────┘ │   │                 │
│                 │   │                 │   │ 封装 JWT 算法   │
│                 │   │ 封装操作执行逻辑 │   │ 可替换不同实现   │
└─────────────────┘   │ 支持撤销(可选)  │   │                 │
                      └─────────────────┘   └─────────────────┘
```

### 2.2 策略模式 (Strategy Pattern)

**定义**：定义算法族，分别封装起来，让它们之间可以互相替换。

**应用场景**：JWT 生成和验证算法。

**接口定义**：

```java
// TokenGenerator.java
public interface TokenGenerator {
    TokenGenerationResult generate(String userId, String subject, 
                                    LocalDateTime issuedAt, LocalDateTime expiresAt);
    String getAlgorithm();
}

// TokenValidator.java
public interface TokenValidator {
    ValidationResult validate(String tokenValue);
    Claims parseQuietly(String tokenValue);
    String extractUserId(String tokenValue);
    String extractJwtId(String tokenValue);
}
```

**实现类**：

| 实现类 | 说明 |
|--------|------|
| `JwtTokenGenerator` | 使用 JJWT 库生成 JWT |
| `JwtTokenValidator` | 使用 JJWT 库验证 JWT |

**扩展性**：未来可以添加：
- `PasetoTokenGenerator`：使用 PASETO 标准
- `OpaqueTokenGenerator`：使用不透明令牌（引用令牌）

### 2.3 命令模式 (Command Pattern)

**定义**：将请求封装为对象，从而可以用不同的请求、队列或日志来参数化其他对象。

**应用场景**：Token 的各种操作（生成、验证、续签、作废）。

**接口定义**：

```java
public interface TokenCommand<R> {
    R execute();
    TokenCommandType getType();
}
```

**命令类型**：

```java
public enum TokenCommandType {
    GENERATE,    // 生成 Token
    VALIDATE,    // 验证 Token
    RENEW,       // 续签 Token
    INVALIDATE,  // 作废 Token
    GET_INFO     // 获取 Token 信息
}
```

**命令实现类**：

| 命令类 | 职责 | 返回类型 |
|--------|------|----------|
| `GenerateTokenCommand` | 生成新 Token | `String` (tokenValue) |
| `ValidateTokenCommand` | 验证 Token 有效性 | `Boolean` |
| `RenewTokenCommand` | 续签 Token | `String` (新 tokenValue) |
| `InvalidateTokenCommand` | 作废 Token | `Boolean` |
| `GetTokenInfoCommand` | 获取 Token 详情 | `Optional<TokenInfo>` |

**命令执行示例**：

```java
// InvalidateTokenCommand.java
@Override
public Boolean execute() {
    Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);
    
    if (!tokenOpt.isPresent()) {
        return false;
    }
    
    Token token = tokenOpt.get();
    
    if (token.getStatus() != TokenStatus.ACTIVE) {
        return false;
    }
    
    // 使用原子更新，解决竞态条件
    return tokenStore.updateStatusIfActive(tokenValue, TokenStatus.INVALIDATED);
}
```

### 2.4 工厂模式 (Factory Pattern)

**定义**：定义创建对象的接口，让子类决定实例化哪一个类。

**应用场景**：根据操作类型创建对应的命令对象。

**工厂实现**：

```java
@Component
public class TokenOperationFactory {

    private final TokenProperties tokenProperties;
    private final TokenStore tokenStore;
    private final TokenGenerator tokenGenerator;
    private final TokenValidator tokenValidator;

    public TokenCommand<String> createGenerateCommand(String userId, 
                                                        String subject, 
                                                        Long expireSeconds) {
        return new GenerateTokenCommand(
            userId, subject, expireSeconds,
            tokenProperties, tokenGenerator, tokenStore
        );
    }

    public TokenCommand<Boolean> createValidateCommand(String tokenValue) {
        return new ValidateTokenCommand(
            tokenValue, tokenStore, tokenValidator
        );
    }

    // ... 其他创建方法
}
```

**工厂的优势**：

1. **封装创建逻辑**：调用者不需要知道命令的构造细节
2. **集中管理依赖**：所有命令的依赖都由工厂注入
3. **易于扩展**：添加新命令只需添加工厂方法

### 2.5 调度器模式 (Dispatcher Pattern)

**定义**：统一协调多个组件的协作，提供简洁的入口。

**应用场景**：统一调度 Token 操作，协调工厂、命令、策略的协作。

**调度器实现**：

```java
@Component
public class TokenOperationDispatcher {

    private final TokenOperationFactory operationFactory;

    public String generateToken(String userId, String subject, Long expireSeconds) {
        TokenCommand<String> command = operationFactory.createGenerateCommand(
            userId, subject, expireSeconds
        );
        return command.execute();
    }

    public boolean validateToken(String tokenValue) {
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            return false;
        }
        TokenCommand<Boolean> command = operationFactory.createValidateCommand(tokenValue);
        return command.execute();
    }

    // ... 其他调度方法
}
```

**调度器的优势**：

1. **统一入口**：所有操作通过一个入口
2. **参数校验**：在调度层进行参数校验
3. **异常处理**：统一处理异常情况
4. **日志记录**：统一日志格式

---

## 3. 重构细节

### 3.1 新增文件

| 文件路径 | 设计模式 | 说明 |
|----------|----------|------|
| `strategy/TokenGenerator.java` | 策略模式 | Token 生成策略接口 |
| `strategy/TokenGenerationResult.java` | - | 生成结果封装类 |
| `strategy/TokenValidator.java` | 策略模式 | Token 验证策略接口 |
| `strategy/JwtTokenGenerator.java` | 策略模式 | JWT 生成实现 |
| `strategy/JwtTokenValidator.java` | 策略模式 | JWT 验证实现 |
| `command/TokenCommand.java` | 命令模式 | 命令接口 |
| `command/TokenCommandType.java` | - | 命令类型枚举 |
| `command/GenerateTokenCommand.java` | 命令模式 | 生成 Token 命令 |
| `command/ValidateTokenCommand.java` | 命令模式 | 验证 Token 命令 |
| `command/RenewTokenCommand.java` | 命令模式 | 续签 Token 命令 |
| `command/InvalidateTokenCommand.java` | 命令模式 | 作废 Token 命令 |
| `command/GetTokenInfoCommand.java` | 命令模式 | 获取 Token 信息命令 |
| `factory/TokenOperationFactory.java` | 工厂模式 | 命令工厂 |
| `dispatcher/TokenOperationDispatcher.java` | 调度器模式 | 操作调度器 |

### 3.2 修改文件

#### 3.2.1 TokenService (API 兼容层)

**重构前**：包含所有业务逻辑

**重构后**：API 兼容层，委托给调度器

```java
@Service
public class TokenService {

    private final TokenOperationDispatcher dispatcher;
    private final TokenStore tokenStore;
    private final JwtKeyManager jwtKeyManager;

    public String generateToken(String userId, String subject, Long expireSeconds) {
        log.info("开始生成Token - userId: {}", userId);
        String tokenValue = dispatcher.generateToken(userId, subject, expireSeconds);
        log.info("Token生成成功 - userId: {}", userId);
        return tokenValue;
    }

    public boolean validateToken(String tokenValue) {
        log.debug("开始验证Token");
        boolean valid = dispatcher.validateToken(tokenValue);
        log.debug("Token验证结果: {}", valid);
        return valid;
    }

    // ... 其他方法同样委托给 dispatcher
}
```

**保持的公开 API**：

| 方法签名 | 说明 |
|----------|------|
| `generateToken(String, String, Long)` | 生成 Token |
| `validateToken(String)` | 验证 Token |
| `renewToken(String, Long, boolean)` | 续签 Token |
| `invalidateToken(String)` | 作废 Token |
| `getTokenInfo(String)` | 获取 Token 信息 |
| `parseClaimsQuietly(String)` | 静默解析 JWT（切面依赖） |
| `clearExpiredTokens()` | 清理过期 Token |

#### 3.2.2 TokenStore 接口

**新增方法**：

```java
// 原方法
void updateStatus(String tokenValue, TokenStatus status);

// 新增原子更新方法（解决竞态条件）
boolean updateStatusIfActive(String tokenValue, TokenStatus status);
```

**实现类修改**：

- `JpaTokenStore`：使用数据库原子更新
- `InMemoryTokenStore`：使用锁保证原子性

#### 3.2.3 TokenRepository

**新增方法**：

```java
// 原子更新：只有当前状态为 ACTIVE 时才更新
@Modifying
@Query("UPDATE Token t SET t.status = :status, t.updatedAt = :updatedAt " +
       "WHERE t.tokenValue = :tokenValue AND t.status = TokenStatus.ACTIVE")
int updateStatusIfActive(@Param("tokenValue") String tokenValue,
                          @Param("status") TokenStatus status,
                          @Param("updatedAt") LocalDateTime updatedAt);
```

### 3.3 并发问题修复

#### 3.3.1 问题分析

**重构前的代码**：

```java
// 存在竞态条件
public boolean invalidateToken(String tokenValue) {
    Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);
    
    if (!tokenOpt.isPresent()) {
        return false;
    }
    
    Token token = tokenOpt.get();
    
    // 检查状态
    if (token.getStatus() != TokenStatus.ACTIVE) {
        return false;
    }
    
    // 更新状态 - 这里存在竞态条件！
    tokenStore.updateStatus(tokenValue, TokenStatus.INVALIDATED);
    
    return true;
}
```

**竞态条件场景**：

```
时间线：
─────────────────────────────────────────────────────────────────>

线程A: findByTokenValue → 看到状态 ACTIVE
线程B: findByTokenValue → 看到状态 ACTIVE (还没更新)
线程A: updateStatus → 更新为 INVALIDATED
线程B: updateStatus → 再次更新，也认为成功
```

**结果**：两个线程都认为自己成功了，但实际上只应该有一个成功。

#### 3.3.2 解决方案

**原子更新**：使用数据库条件更新或锁保证原子性。

**JPA 实现**：

```java
// TokenRepository.java
@Modifying
@Query("UPDATE Token t SET t.status = :status, t.updatedAt = :updatedAt " +
       "WHERE t.tokenValue = :tokenValue AND t.status = com.example.tokenservice.model.TokenStatus.ACTIVE")
int updateStatusIfActive(@Param("tokenValue") String tokenValue,
                          @Param("status") TokenStatus status,
                          @Param("updatedAt") LocalDateTime updatedAt);
```

**内存实现**：

```java
// InMemoryTokenStore.java
@Override
public boolean updateStatusIfActive(String tokenValue, TokenStatus status) {
    lock.writeLock().lock();
    try {
        Token token = tokenMap.get(tokenValue);
        if (token != null && token.getStatus() == TokenStatus.ACTIVE) {
            token.setStatus(status);
            token.setUpdatedAt(LocalDateTime.now());
            tokenMap.put(tokenValue, token);
            return true;
        }
        return false;
    } finally {
        lock.writeLock().unlock();
    }
}
```

**修复后的命令**：

```java
// InvalidateTokenCommand.java
@Override
public Boolean execute() {
    Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);
    
    if (!tokenOpt.isPresent()) {
        return false;
    }
    
    Token token = tokenOpt.get();
    
    if (token.getStatus() != TokenStatus.ACTIVE) {
        return false;
    }
    
    // 原子更新：只有真正更新了才返回 true
    return tokenStore.updateStatusIfActive(tokenValue, TokenStatus.INVALIDATED);
}
```

---

## 4. 测试结果

### 4.1 测试覆盖

| 测试类 | 测试数量 | 说明 |
|--------|----------|------|
| `TokenStatisticsLinkIntegrationTest` | 20 | 统计链路集成测试 |
| `TokenServiceIntegrationTest` | 4 | Token 服务集成测试 |
| `TokenServiceTest` | 8 | Token 服务单元测试 |
| **总计** | **32** | |

### 4.2 测试结果

```
[INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 4.3 关键测试验证

#### 4.3.1 并发作废测试

**测试场景**：10 个线程同时作废同一个 Token

**预期结果**：只有 1 个线程成功

```java
@Test
void concurrentTokenInvalidation_ShouldBeThreadSafe() throws InterruptedException {
    String token = tokenService.generateToken("invalidate-user", "test", 3600L);
    
    int threadCount = 10;
    CountDownLatch latch = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                boolean success = tokenService.invalidateToken(token);
                if (success) {
                    successCount.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    executor.shutdown();

    assertEquals(1, successCount.get());  // 只有一个成功
}
```

**测试结果**：✅ 通过

#### 4.3.2 API 兼容性测试

所有原有测试都通过，证明：

- `TokenService` 的公开 API 完全兼容
- 重构没有改变功能行为
- 所有依赖 `TokenService` 的组件（Controller、Aspect、其他 Service）无需修改

---

## 5. 架构收益

### 5.1 设计原则遵循

| 设计原则 | 遵循情况 | 说明 |
|----------|----------|------|
| 单一职责 (SRP) | ✅ 完全遵循 | 每个类只负责一件事 |
| 开闭原则 (OCP) | ✅ 完全遵循 | 可添加新命令而不修改现有代码 |
| 依赖倒置 (DIP) | ✅ 完全遵循 | 依赖抽象，不依赖具体实现 |
| 接口隔离 (ISP) | ✅ 完全遵循 | 接口小而专一 |
| 里氏替换 (LSP) | ✅ 完全遵循 | 实现类可替换接口 |

### 5.2 可扩展性

**添加新操作类型的步骤**：

1. 新增命令实现类（如 `RefreshTokenCommand`）
2. 在 `TokenCommandType` 枚举中添加新类型（可选）
3. 在 `TokenOperationFactory` 中添加工厂方法
4. 在 `TokenOperationDispatcher` 中添加调度方法

**示例**：添加 Token 刷新功能

```java
// 1. 新增命令
public class RefreshTokenCommand implements TokenCommand<String> {
    // ... 实现
}

// 2. 添加工厂方法
public TokenCommand<String> createRefreshCommand(...) {
    return new RefreshTokenCommand(...);
}

// 3. 添加调度方法
public String refreshToken(...) {
    TokenCommand<String> command = factory.createRefreshCommand(...);
    return command.execute();
}

// 4. TokenService 新增方法（可选，保持 API 兼容）
public String refreshToken(...) {
    return dispatcher.refreshToken(...);
}
```

### 5.3 可测试性

**单元测试优势**：

1. **命令可单独测试**：每个命令可以独立测试，不需要完整的服务上下文
2. **策略可 Mock**：`TokenGenerator` 和 `TokenValidator` 可以被 Mock，便于测试命令逻辑
3. **工厂可验证**：可以验证工厂是否创建了正确的命令

**测试示例**：

```java
// 单独测试命令
class InvalidateTokenCommandTest {
    
    @Mock
    private TokenStore tokenStore;
    
    @Test
    void execute_WithActiveToken_ShouldReturnTrue() {
        // 准备
        String tokenValue = "test-token";
        Token token = createToken(tokenValue, TokenStatus.ACTIVE);
        when(tokenStore.findByTokenValue(tokenValue)).thenReturn(Optional.of(token));
        when(tokenStore.updateStatusIfActive(tokenValue, TokenStatus.INVALIDATED)).thenReturn(true);
        
        // 执行
        InvalidateTokenCommand command = new InvalidateTokenCommand(tokenValue, tokenStore);
        boolean result = command.execute();
        
        // 验证
        assertTrue(result);
        verify(tokenStore).updateStatusIfActive(tokenValue, TokenStatus.INVALIDATED);
    }
}
```

### 5.4 性能对比

**并发场景**：10 个线程同时作废同一个 Token

| 指标 | 重构前 | 重构后 |
|------|--------|--------|
| 成功线程数 | 不确定（竞态条件） | 固定 1 个（原子操作） |
| 数据一致性 | 可能不一致 | 完全一致 |
| 可预测性 | 低 | 高 |

---

## 6. 重构总结

### 6.1 重构内容清单

| 类别 | 数量 | 说明 |
|------|------|------|
| 新增接口 | 3 | `TokenGenerator`, `TokenValidator`, `TokenCommand` |
| 新增实现类 | 9 | 策略、命令的具体实现 |
| 新增工厂类 | 1 | `TokenOperationFactory` |
| 新增调度器 | 1 | `TokenOperationDispatcher` |
| 修改接口 | 1 | `TokenStore`（新增原子方法） |
| 修改实现类 | 3 | `TokenService`, `JpaTokenStore`, `InMemoryTokenStore` |
| 修改测试类 | 1 | `TokenServiceTest`（适配新 API） |
| 新增数据访问 | 1 | `TokenRepository.updateStatusIfActive` |

### 6.2 关键设计模式应用

```
┌─────────────────────────────────────────────────────────────────┐
│                        调用流程                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  TokenService.generateToken(userId, subject, expireSeconds)    │
│           │                                                       │
│           ▼ 委托                                                 │
│  TokenOperationDispatcher.generateToken(...)                    │
│           │                                                       │
│           ▼ 工厂创建                                             │
│  TokenOperationFactory.createGenerateCommand(...)               │
│           │                                                       │
│           ▼ 返回命令                                             │
│  GenerateTokenCommand.execute()                                  │
│           │                                                       │
│           ├─► TokenGenerator.generate()  ◄── 策略模式           │
│           └─► TokenStore.save()                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 6.3 重构原则

1. **保持 API 兼容**：`TokenService` 的所有公开方法签名不变
2. **保持功能不变**：重构只改变代码组织，不改变行为
3. **修复已知问题**：借机修复了 `invalidateToken` 的竞态条件
4. **测试先行**：重构后所有测试必须通过

---

## 7. 附录

### 7.1 类依赖关系图

```
TokenService (API兼容层)
    │
    ├──► TokenOperationDispatcher (调度器)
    │         │
    │         └──► TokenOperationFactory (工厂)
    │                   │
    │                   ├──► TokenProperties
    │                   ├──► TokenStore
    │                   ├──► TokenGenerator (策略接口)
    │                   │         └──► JwtTokenGenerator (实现)
    │                   │                   └──► JwtKeyManager
    │                   │
    │                   └──► TokenValidator (策略接口)
    │                             └──► JwtTokenValidator (实现)
    │                                       └──► JwtKeyManager
    │
    ├──► TokenStore (直接依赖，用于 clearExpiredTokens)
    │
    └──► JwtKeyManager (直接依赖，用于 parseClaimsQuietly，切面依赖)
```

### 7.2 命令-工厂-策略协作示例

```java
// 生成 Token 的完整流程
public String generateToken(String userId, String subject, Long expireSeconds) {
    
    // 1. 调度器：入口
    // TokenOperationDispatcher.generateToken()
    
    // 2. 工厂：创建命令
    // TokenOperationFactory.createGenerateCommand(userId, subject, expireSeconds)
    // 返回: GenerateTokenCommand 实例
    
    // 3. 命令：执行操作
    // GenerateTokenCommand.execute()
    //   - 计算过期时间
    //   - 调用策略生成 JWT
    //   - 保存到存储
    
    // 4. 策略：生成 JWT
    // JwtTokenGenerator.generate()
    //   - 构建 JWT
    //   - 签名
    //   - 返回 TokenGenerationResult
    
    // 5. 返回结果
}
```

### 7.3 扩展指南

#### 添加新的 Token 操作

1. **创建命令类**：
```java
public class MyNewOperationCommand implements TokenCommand<MyResultType> {
    // 实现 execute() 方法
}
```

2. **在工厂添加创建方法**：
```java
public TokenCommand<MyResultType> createMyNewOperationCommand(...) {
    return new MyNewOperationCommand(...);
}
```

3. **在调度器添加调度方法**：
```java
public MyResultType myNewOperation(...) {
    TokenCommand<MyResultType> command = factory.createMyNewOperationCommand(...);
    return command.execute();
}
```

4. **在 TokenService 添加 API（可选）**：
```java
public MyResultType myNewOperation(...) {
    return dispatcher.myNewOperation(...);
}
```

---

**文档版本**：v1.0
**创建日期**：2026-04-27
**重构范围**：TokenService 及相关组件
**测试状态**：✅ 33 个测试全部通过
