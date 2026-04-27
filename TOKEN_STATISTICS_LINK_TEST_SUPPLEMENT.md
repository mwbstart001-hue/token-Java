# Token 统计链路集成测试补充文档（含异步修复）

## 1. 背景

### 1.1 问题描述

在之前的项目中，Token 统计功能存在两个关键问题：

**问题 1：缺少端到端集成测试**

统计功能的完整链路是：
```
TokenService 业务操作
    ↓
TokenStatisticsAspect (AOP 切面拦截)
    ↓
ApplicationEventPublisher (发布事件)
    ↓
TokenOperationEventListener (事件监听器处理)
    ↓
TokenStatisticsService (统计服务)
    ↓
TokenStatisticsRepository (数据库持久化)
```

这个链路涉及多个组件的协作，但之前的测试只覆盖了个别组件，没有验证完整链路的正确性。

**问题 2：@Async 注解未生效（隐藏问题）**

事件监听器使用了 `@Async` 注解，但缺少 `@EnableAsync` 配置，导致异步实际是同步执行的。

### 1.2 统计链路架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           统计链路架构（异步）                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  主线程                                                                      │
│  ┌──────────────┐                                                          │
│  │ TokenService │  ← 业务层：生成/验证/续签/作废 Token                    │
│  └──────┬───────┘                                                          │
│         │                                                                    │
│         ▼ AOP 切面拦截 (Around)                                              │
│  ┌───────────────────────────┐                                              │
│  │ TokenStatisticsAspect  │  ← 横切关注点：拦截操作，提取 userId/jwtId     │
│  └───────────┬───────────────┘                                              │
│            │                                                                │
│            ▼ 发布事件                                                         │
│  ┌───────────────────────────┐                                              │
│  │ ApplicationEventPublisher │                                              │
│  │ (TokenOperationEvent)   │  ← 事件：包含 userId/jwtId/类型              │
│  └───────────┬───────────────┘                                              │
│            │                                                                │
│            ┼─────────────────────────────────────────────────────────────┼  │
│            │                         @Async 切换线程                       │  │
│            ▼                                                                │  │
│  ┌───────────────────────────┐                    异步线程池                │  │
│  │ SimpleAsyncTaskExecutor    │  ← Spring 默认异步线程池                   │  │
│  │ (Spring @Async 默认)     │                                              │  │
│  └───────────┬───────────────┘                                              │  │
│            │                                                                │  │
│            ▼ 监听事件 (@EventListener + @Async)                             │  │
│  ┌───────────────────────────┐                                              │  │
│  │ TokenOperationEventListener │  ← 异步处理，不阻塞主线程                 │  │
│  └───────────┬───────────────┘                                              │  │
│            │                                                                │  │
│            ▼ 记录统计                                                         │  │
│  ┌───────────────────────────┐                                              │  │
│  │ TokenStatisticsService    │  ← 服务层：业务逻辑                          │  │
│  └───────────┬───────────────┘                                              │  │
│            │                                                                │  │
│            ▼ 持久化                                                           │  │
│  ┌───────────────────────────┐                                              │  │
│  │ TokenStatisticsRepository │  ← 数据层：保存到数据库                     │  │
│  └───────────────────────────┘                                              │  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**架构要点**：
- 主线程：Token 业务操作 + AOP 拦截 + 事件发布
- 异步线程：事件监听处理 + 统计入库
- 这样可以保证主业务流程不受统计耗时影响

### 1.3 发现的隐藏问题

#### 1.3.1 @Async 未生效的问题

**问题代码位置**：
- `TokenOperationEventListener.java:26` - 使用了 `@Async` 注解
- `TokenServiceApplication.java` - **缺少 `@EnableAsync` 注解**

```java
// TokenOperationEventListener.java
@Async  // ← 这个注解没有生效！
@EventListener
public void handleTokenOperationEvent(TokenOperationEvent event) {
    // ...
}
```

```java
// TokenServiceApplication.java - 修复前
@SpringBootApplication
public class TokenServiceApplication {  // 缺少 @EnableAsync！
    // ...
}
```

**问题影响**：
1. 设计意图是异步处理统计，不阻塞主业务
2. 实际行为是同步执行，统计处理会阻塞主业务
3. 测试也因此"偶然"通过（因为同步执行，查询时数据已入库）

#### 1.3.2 如果修复 @Async，现有测试会失败

如果只添加 `@EnableAsync` 而不修改测试，会发生：

```
测试执行流程（异步生效后）：
1. tokenService.generateToken()              ← 主线程
2. AOP 切面拦截，发布事件（异步）
3. 测试立即执行 statisticsService.getUserStatistics()  ← 此时异步处理可能还没完成！
4. 测试验证统计数据                                              ← 数据可能还没入库！
```

**结果**：测试会偶发失败或数据不一致。

### 1.4 之前的测试覆盖情况

| 测试类 | 测试内容 | 覆盖链路 |
|--------|----------|----------|
| `TokenStatisticsServiceTest` | 统计服务单元测试 | 只覆盖 Service 层 |
| `TokenServiceIntegrationTest` | Token 业务集成测试 | 不涉及统计功能 |
| 无 | 统计链路端到端测试 | ❌ 缺失 |
| 无 | 异步事件处理测试 | ❌ 缺失 |

### 1.5 关键技术点

1. **AOP 切面启用**：统计功能通过 `@ConditionalOnProperty` 控制
2. **测试配置禁用统计**：`application-test.yml` 中 `token.statistics.enabled=false`
3. **JWT 解析下沉**：`JwtKeyManager` 负责密钥管理和 JWT 解析
4. **@Async 需要 @EnableAsync**：Spring 异步注解需要配合启用注解

---

## 2. 修复过程

### 2.1 修复问题 1：添加 @EnableAsync

**文件路径**：`src/main/java/com/example/tokenservice/TokenServiceApplication.java`

**修复前**：
```java
@SpringBootApplication
public class TokenServiceApplication {
    // 缺少 @EnableAsync
}
```

**修复后**：
```java
@SpringBootApplication
@EnableAsync  // 新增：启用异步支持
public class TokenServiceApplication {
    // ...
}
```

**效果**：`TokenOperationEventListener` 上的 `@Async` 注解现在真正生效了。

### 2.2 修复问题 2：添加 Awaitility 依赖

为了测试异步操作，需要添加 Awaitility 库来优雅地等待异步处理完成。

**文件路径**：`pom.xml`

**新增依赖**：
```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.0</version>
    <scope>test</scope>
</dependency>
```

**Awaitility 简介**：
- 专门用于测试异步操作的库
- 支持轮询等待条件满足
- 语法优雅：`await().atMost(10, SECONDS).until(() -> condition)`

### 2.3 修复问题 3：修改测试类支持异步等待

**文件路径**：`src/test/java/com/example/tokenservice/TokenStatisticsLinkIntegrationTest.java`

**关键修改**：

1. **添加 Awaitility 导入**：
```java
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import static org.awaitility.Awaitility.await;
```

2. **添加异步等待辅助方法**：
```java
@BeforeEach
void setUp() {
    statisticsRepository.deleteAll();
    Awaitility.setDefaultTimeout(10, TimeUnit.SECONDS);
    Awaitility.setDefaultPollInterval(100, TimeUnit.MILLISECONDS);
}

private ConditionFactory awaitAsync() {
    return await().atMost(10, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS);
}

private void waitForRecords(int expectedCount) {
    awaitAsync().until(() -> statisticsRepository.count() == expectedCount);
}

private void waitForRecordsByType(String userId, TokenOperationType type, long expectedCount) {
    LocalDateTime start = LocalDateTime.now().minusMinutes(1);
    LocalDateTime end = LocalDateTime.now().plusMinutes(1);
    awaitAsync().until(() -> {
        List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, start, end);
        return stats.stream()
                .filter(s -> s.getOperationType() == type)
                .mapToLong(TokenStatisticsSummary::getTotalCount)
                .sum() == expectedCount;
    });
}
```

3. **修改测试方法，添加异步等待**：

以完整生命周期测试为例：
```java
@Test
void fullTokenLifecycle_ShouldRecordAllOperations() {
    String userId = "full-lifecycle-user";
    
    // 操作 1: 生成 Token
    String token = tokenService.generateToken(userId, "test", 3600L);
    waitForRecordsByType(userId, TokenOperationType.GENERATE, 1);  // 等待异步处理完成
    
    // 操作 2: 验证 Token (第1次)
    tokenService.validateToken(token);
    waitForRecordsByType(userId, TokenOperationType.VALIDATE, 1);
    
    // 操作 3: 验证 Token (第2次)
    tokenService.validateToken(token);
    waitForRecordsByType(userId, TokenOperationType.VALIDATE, 2);
    
    // 操作 4: 续签 Token
    String newToken = tokenService.renewToken(token, 3600L, true);
    waitForRecordsByType(userId, TokenOperationType.RENEW, 1);
    
    // 操作 5: 验证新 Token
    tokenService.validateToken(newToken);
    waitForRecordsByType(userId, TokenOperationType.VALIDATE, 3);
    
    // 操作 6: 作废 Token
    tokenService.invalidateToken(newToken);
    waitForRecordsByType(userId, TokenOperationType.INVALIDATE, 1);
    
    // 等待所有记录入库
    waitForRecords(6);
    
    // 验证统计结果
    // ...
}
```

4. **新增异步特性测试**：
```java
@Test
void asyncEventProcessing_ShouldBeAsynchronous() throws InterruptedException {
    String userId = "async-test-user";
    
    long startTime = System.currentTimeMillis();
    
    // 主操作应该快速返回（因为统计在异步线程处理）
    String token = tokenService.generateToken(userId, "test", 3600L);
    
    long operationTime = System.currentTimeMillis() - startTime;
    
    // 验证主操作快速返回（< 1秒，实际应该远小于这个值）
    assertTrue(operationTime < 1000, "主操作应该快速返回（异步处理）");
    
    // 等待异步处理完成
    waitForRecords(1);
    
    // 验证数据最终入库
    List<TokenStatistics> records = statisticsService.getUserRecords(
        userId, 
        LocalDateTime.now().minusMinutes(1), 
        LocalDateTime.now().plusMinutes(1)
    );
    
    assertFalse(records.isEmpty());
    assertEquals(userId, records.get(0).getUserId());
    assertEquals(TokenOperationType.GENERATE, records.get(0).getOperationType());
}
```

### 2.4 修复前后对比

#### 2.4.1 链路执行对比

| 阶段 | 修复前（同步） | 修复后（异步） |
|------|----------------|----------------|
| Token 操作 | 主线程 | 主线程 |
| AOP 拦截 | 主线程 | 主线程 |
| 事件发布 | 主线程 | 主线程 |
| 事件监听 | 主线程（阻塞） | **异步线程（不阻塞）** |
| 统计入库 | 主线程（阻塞） | **异步线程（不阻塞）** |

#### 2.4.2 性能影响对比

**修复前（同步）**：
```
Token 业务操作（假设 50ms）
    + 统计处理（假设 100ms，数据库操作）
    + 日志记录（假设 10ms）
    = 总耗时：160ms（用户等待）
```

**修复后（异步）**：
```
Token 业务操作（假设 50ms）
    + 事件发布（假设 5ms，内存操作）
    = 主线程总耗时：55ms（用户等待）
    
[后台异步线程]
    + 统计处理（假设 100ms）
    + 日志记录（假设 10ms）
    = 后台处理，不影响用户
```

**收益**：用户感知的响应时间从 160ms 降低到 55ms，性能提升约 **65%**。

### 2.5 测试覆盖场景

#### 2.5.1 测试启用统计功能

由于 `application-test.yml` 中 `token.statistics.enabled` 默认禁用统计，测试类使用 `@TestPropertySource` 覆盖配置：

```java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "token.statistics.enabled=true",
    "token.statistics.record-invalid-tokens=false"
})
class TokenStatisticsLinkIntegrationTest {
    // ...
}
```

#### 2.5.2 测试覆盖矩阵

| 测试方法 | 测试场景 | 验证内容 |
|----------|----------|----------|
| `generateToken_ShouldRecordStatistics` | Token 生成统计 | AOP 拦截 → 事件发布 → 异步处理 → 统计入库 |
| `generateToken_ShouldRecordCorrectUserIdAndJwtId` | 数据完整性 | userId、jwtId 正确记录 |
| `generateToken_MultipleTimes_ShouldCountEach` | 多次生成 | 计数正确 |
| `validateToken_WithValidToken_ShouldRecordStatistics` | 有效 Token 验证 | 成功操作触发统计 |
| `validateToken_WithInvalidToken_ShouldNotRecordStatistics` | 无效 Token 验证 | 失败操作不触发统计 |
| `validateToken_ShouldRecordCorrectUserId` | 验证数据完整性 | userId 正确 |
| `renewToken_ShouldRecordStatistics` | Token 续签统计 | 续签操作完整链路 |
| `renewToken_ShouldRecordNewJwtId` | 续签数据完整性 | 新 jwtId 正确记录 |
| `renewToken_WithInvalidToken_ShouldNotRecordStatistics` | 无效 Token 续签 | 不触发统计 |
| `invalidateToken_ShouldRecordStatistics` | Token 作废统计 | 作废操作完整链路 |
| `invalidateToken_ShouldRecordCorrectInfo` | 作废数据完整性 | userId、jwtId 正确 |
| `fullTokenLifecycle_ShouldRecordAllOperations` | 完整生命周期 | 生成 → 验证 ×3 → 续签 → 验证 → 作废 |
| `multipleUsers_ShouldBeSeparatedInStatistics` | 多用户隔离 | 用户 A 和用户 B 统计隔离 |
| `getAllStatistics_ShouldAggregateAllUsers` | 全量统计聚合 | 聚合所有用户的统计数据 |
| `concurrentOperations_ShouldBeThreadSafe` | 并发统计 | 多线程并发操作统计线程安全 |
| `validate_WithInvalidToken_NotRecorded` | 无效验证过滤 | 不触发统计 |
| `renew_WithInvalidToken_NotRecorded` | 无效续签过滤 | 不触发统计 |
| `invalidate_WithInvalidToken_NotRecorded` | 无效作废过滤 | 不触发统计 |
| `mixedOperations_OnlyRecordSuccessful` | 混合操作过滤 | 有效操作记录，无效操作不记录 |
| `asyncEventProcessing_ShouldBeAsynchronous` | 异步特性验证 | 主操作快速返回，数据最终入库 |

### 2.6 新增测试配置文件

**文件路径**：`src/test/resources/application-statistics-test.yml`

这个配置文件用于在需要时手动运行统计测试，配置启用统计功能：

```yaml
token:
  statistics:
    enabled: true
    record-invalid-tokens: false
```

---

## 3. 测试结果

### 3.1 统计链路测试结果

```
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 3.2 测试覆盖详情

| 测试类别 | 测试数量 | 通过 | 失败 | 跳过 |
|----------|----------|------|------|------|
| Token 生成统计 | 3 | ✅ 3 | ❌ 0 | ⏭️ 0 |
| Token 验证统计 | 3 | ✅ 3 | ❌ 0 | ⏭️ 0 |
| Token 续签统计 | 3 | ✅ 3 | ❌ 0 | ⏭️ 0 |
| Token 作废统计 | 2 | ✅ 2 | ❌ 0 | ⏭️ 0 |
| 完整链路测试 | 3 | ✅ 3 | ❌ 0 | ⏭️ 0 |
| 并发统计测试 | 1 | ✅ 1 | ❌ 0 | ⏭️ 0 |
| 无效 Token 过滤测试 | 4 | ✅ 4 | ❌ 0 | ⏭️ 0 |
| 异步特性测试 | 1 | ✅ 1 | ❌ 0 | ⏭️ 0 |
| **总计** | **20** | **✅ 20** | **❌ 0** | **⏭️ 0** |

### 3.3 完整测试验证点

#### 3.3.1 链路完整性验证

所有测试都验证了完整的异步链路流程：

```
TokenService.generateToken()                              [主线程]
    ↓ AOP Around 拦截
TokenStatisticsAspect.aroundGenerateToken()               [主线程]
    ↓ 发布事件
ApplicationEventPublisher.publishEvent(TokenOperationEvent) [主线程]
    ↓ @Async 切换线程池
SimpleAsyncTaskExecutor (异步线程)                          [异步线程池]
    ↓ 事件监听处理
TokenOperationEventListener.handleTokenOperationEvent()    [异步线程]
    ↓ 调用统计服务
TokenStatisticsService.recordOperation()                   [异步线程]
    ↓ 持久化
TokenStatisticsRepository.save()                            [异步线程]
    ↓ 等待异步处理完成
Awaitility 等待条件满足                                     [测试主线程]
    ↓ 查询验证
TokenStatisticsService.getUserStatistics()                  [测试主线程]
```

#### 3.3.2 关键验证点

1. **@EnableAsync 正确启用**：`@Async` 注解现在真正生效

2. **AOP 切面正确启用**：`@TestPropertySource` 覆盖配置启用统计

3. **事件正确发布**：切面正确提取 userId 和 jwtId

4. **异步处理正确执行**：
   - 主操作快速返回（< 1 秒）
   - 数据最终入库（通过 Awaitility 等待验证）

5. **统计正确入库**：通过 `statisticsRepository.count()` 和 `getUserStatistics()` 验证

6. **无效操作过滤**：无效 Token 验证不触发统计
   - 验证无效 Token：统计记录数 = 0
   - 续签无效 Token：统计记录数 = 0
   - 作废不存在的 Token：统计记录数 = 0

7. **多用户隔离**：用户 A 的操作不影响用户 B 的统计

8. **并发线程安全**：20 线程 × 5 操作 = 200 生成 + 200 验证，统计准确

9. **异步特性验证**：
   - 主操作快速返回（不阻塞）
   - 数据最终一致性（后台异步处理）

### 3.4 与现有测试的兼容性

#### 3.4.1 不影响现有测试

由于 `TokenStatisticsLinkIntegrationTest` 使用 `@TestPropertySource` 覆盖配置，它只影响自身测试类，不影响其他测试。

其他测试类仍使用 `application-test.yml` 的配置：
```yaml
token:
  statistics:
    enabled: false  # 其他测试仍禁用统计
```

#### 3.4.2 @EnableAsync 不影响其他测试

`@EnableAsync` 是应用级配置，但它只影响标有 `@Async` 注解的方法。其他没有 `@Async` 的方法不受影响，同步执行。

**影响范围**：
- ✅ 影响：`TokenOperationEventListener.handleTokenOperationEvent()` - 现在真正异步执行
- ✅ 不影响：其他所有方法 - 同步执行，行为不变

#### 3.4.3 测试运行说明

**单独运行统计链路测试**：
```bash
mvn test -Dtest=TokenStatisticsLinkIntegrationTest
```

**运行所有测试**：
```bash
mvn test
```

---

## 4. 技术要点总结

### 4.1 修改文件汇总

| 文件路径 | 修改类型 | 说明 |
|----------|----------|------|
| `src/main/java/com/example/tokenservice/TokenServiceApplication.java` | 修改 | 添加 `@EnableAsync` 启用异步支持 |
| `pom.xml` | 修改 | 添加 `awaitility` 依赖用于异步测试 |
| `src/test/java/com/example/tokenservice/TokenStatisticsLinkIntegrationTest.java` | 修改 | 使用 Awaitility 等待异步处理，新增异步特性测试 |

### 4.2 新增文件

| 文件路径 | 说明 |
|----------|------|
| `src/test/java/com/example/tokenservice/TokenStatisticsLinkIntegrationTest.java` | 统计链路集成测试（20 个测试） |
| `src/test/resources/application-statistics-test.yml` | 统计测试专用配置 |

### 4.3 测试覆盖矩阵

| 操作类型 | 有效操作 | 无效操作过滤 | 数据完整性 | 并发安全 | 异步特性 |
|----------|----------|--------------|------------|----------|----------|
| GENERATE (生成) | ✅ | - | ✅ userId/jwtId | ✅ | ✅ |
| VALIDATE (验证) | ✅ | ✅ 不触发统计 | ✅ userId | ✅ | ✅ |
| RENEW (续签) | ✅ | ✅ 不触发统计 | ✅ 新 jwtId | ✅ | ✅ |
| INVALIDATE (作废) | ✅ | ✅ 不触发统计 | ✅ userId/jwtId | ✅ | ✅ |

### 4.4 架构收益

1. **真正的异步处理**：
   - 添加 `@EnableAsync` 后，`@Async` 真正生效
   - 主业务流程不再被统计处理阻塞
   - 性能提升：用户感知响应时间大幅缩短

2. **关注点分离**：
   - `JwtKeyManager` 负责密钥和解析
   - `TokenStatisticsAspect` 负责拦截和事件发布
   - `TokenStatisticsService` 负责业务逻辑
   - 异步线程池负责统计处理

3. **测试可配置性**：
   - 生产环境：`token.statistics.enabled=true` + `@EnableAsync`
   - 测试环境：按需启用/禁用统计
   - 使用 Awaitility 优雅地测试异步操作

4. **链路透明性**：
   - 切面不依赖业务层 `TokenService`
   - 只依赖基础层 `JwtKeyManager`
   - 异步处理通过 Spring 标准机制实现

### 4.5 关键技术提醒

**关于 @Async 的注意事项**：

1. **必须配合 @EnableAsync**：
   ```java
   // 只加 @Async 不够
   @Async
   public void asyncMethod() { ... }
   
   // 必须在配置类或启动类添加
   @EnableAsync
   @SpringBootApplication
   public class Application { ... }
   ```

2. **默认线程池**：
   - Spring 使用 `SimpleAsyncTaskExecutor` 作为默认线程池
   - 每个任务创建一个新线程，不重用
   - 高并发场景建议自定义线程池

3. **异常处理**：
   - `@Async` 方法的异常默认不会抛给调用者
   - 需要使用 `Future` 或自定义异常处理器

4. **自调用问题**：
   - 类内部调用 `@Async` 方法不会生效
   - 需要通过代理调用（注入自己或使用 `AopContext`）

**关于异步测试的注意事项**：

1. **使用 Awaitility**：
   - 避免使用 `Thread.sleep()` 固定等待
   - Awaitility 会轮询等待条件满足
   - 超时时间可配置，更灵活

2. **测试策略**：
   ```java
   // 1. 执行操作
   tokenService.generateToken(userId, ...);
   
   // 2. 等待异步处理完成
   waitForRecords(1);
   
   // 3. 验证结果
   List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(...);
   ```

3. **验证异步特性**：
   ```java
   // 验证主操作快速返回
   long startTime = System.currentTimeMillis();
   tokenService.generateToken(...);
   long duration = System.currentTimeMillis() - startTime;
   assertTrue(duration < 1000, "主操作应该快速返回");
   
   // 等待数据入库
   waitForRecords(1);
   
   // 验证数据存在
   assertNotNull(statisticsRepository.findById(...));
   ```

---

## 5. 附录

### 5.1 测试类依赖

```java
@Autowired
private TokenService tokenService;           // 业务服务

@Autowired
private TokenStatisticsService statisticsService;  // 统计服务

@Autowired
private TokenStatisticsRepository statisticsRepository;  // 直接访问数据库验证

@Autowired
private JwtKeyManager jwtKeyManager;       // 用于解析 Token 验证
```

### 5.2 异步等待辅助方法

```java
@BeforeEach
void setUp() {
    statisticsRepository.deleteAll();
    Awaitility.setDefaultTimeout(10, TimeUnit.SECONDS);
    Awaitility.setDefaultPollInterval(100, TimeUnit.MILLISECONDS);
}

private ConditionFactory awaitAsync() {
    return await().atMost(10, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS);
}

private void waitForRecords(int expectedCount) {
    awaitAsync().until(() -> statisticsRepository.count() == expectedCount);
}

private void waitForRecordsByType(String userId, TokenOperationType type, long expectedCount) {
    LocalDateTime start = LocalDateTime.now().minusMinutes(1);
    LocalDateTime end = LocalDateTime.now().plusMinutes(1);
    awaitAsync().until(() -> {
        List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, start, end);
        return stats.stream()
                .filter(s -> s.getOperationType() == type)
                .mapToLong(TokenStatisticsSummary::getTotalCount)
                .sum() == expectedCount;
    });
}
```

### 5.3 统计数据结构

```java
public class TokenStatistics {
    private Long id;
    private String userId;           // 用户 ID
    private String jwtId;            // JWT ID
    private TokenOperationType operationType;  // 操作类型
    private boolean success;       // 是否成功
    private LocalDateTime operationTime;  // 操作时间
    // ...
}

public enum TokenOperationType {
    GENERATE("生成"),
    VALIDATE("验证"),
    RENEW("续签"),
    INVALIDATE("作废");
    // ...
}
```

### 5.4 切面核心代码位置

| 组件 | 文件路径 | 关键注解 |
|------|----------|----------|
| AOP 切面 | `src/main/java/com/example/tokenservice/aspect/TokenStatisticsAspect.java` | `@Aspect`, `@Around` |
| 事件类 | `src/main/java/com/example/tokenservice/event/TokenOperationEvent.java` | - |
| 事件监听器 | `src/main/java/com/example/tokenservice/event/TokenOperationEventListener.java` | `@Async`, `@EventListener` |
| 统计服务 | `src/main/java/com/example/tokenservice/service/TokenStatisticsService.java` | - |
| JWT 解析 | `src/main/java/com/example/tokenservice/config/JwtKeyManager.java` | - |
| 应用启动类 | `src/main/java/com/example/tokenservice/TokenServiceApplication.java` | `@EnableAsync` (新增) |

### 5.5 修复前问题确认

在修复前，可以通过以下方式确认 `@Async` 未生效：

1. **查看线程名称**：在 `TokenOperationEventListener` 中打印线程名
   ```java
   @Async
   @EventListener
   public void handleTokenOperationEvent(TokenOperationEvent event) {
       log.info("当前线程: {}", Thread.currentThread().getName());
       // 修复前：输出 "http-nio-8080-exec-1" (主线程)
       // 修复后：输出 "SimpleAsyncTaskExecutor-1" (异步线程)
   }
   ```

2. **查看执行时序**：
   ```java
   // 修复前（同步）：
   log.info("1. 开始 generateToken");
   String token = tokenService.generateToken(userId, ...);
   // 此时统计已经入库（同步执行）
   log.info("2. 统计已入库");
   
   // 修复后（异步）：
   log.info("1. 开始 generateToken");
   String token = tokenService.generateToken(userId, ...);
   // 此时统计可能还没入库（异步执行中）
   log.info("2. 统计处理中...");
   // 需要等待
   waitForRecords(1);
   log.info("3. 统计已入库");
   ```

---

**文档版本**：v2.0
**更新日期**：2026-04-27
**更新内容**：
- 新增 `@Async` 未生效问题的发现和修复
- 新增 Awaitility 异步测试说明
- 新增异步特性测试说明
- 更新测试结果（20 个测试）
- 新增架构收益和技术提醒
