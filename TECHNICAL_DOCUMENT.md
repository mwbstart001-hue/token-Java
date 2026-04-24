# Token 校验服务技术文档

## 1. 项目概述

Token 校验服务是一个基于 JDK 1.8 + Spring Boot 开发的轻量级 Token 管理服务，提供 Token 的生成、验证、作废等核心功能。服务采用模块化设计，支持内存存储和持久化存储两种模式，所有操作均保证线程安全。

## 2. 技术栈

### 2.1 核心技术
| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 1.8 | Java 开发环境 |
| Spring Boot | 2.7.18 | 应用框架 |
| Spring Data JPA | 2.7.18 | 数据持久层 |
| H2 Database | 2.1.x | 嵌入式数据库 |
| JJWT | 0.11.5 | JWT 生成与解析库 |

### 2.2 项目依赖
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## 3. 项目结构

```
token-service/
├── src/main/java/com/example/tokenservice/
│   ├── TokenServiceApplication.java    # 主启动类
│   ├── config/
│   │   └── TokenProperties.java        # Token 配置属性
│   ├── controller/
│   │   └── TokenController.java        # REST API 控制器
│   ├── dto/
│   │   ├── ApiResponse.java             # 统一响应封装
│   │   ├── TokenGenerateRequest.java    # Token 生成请求
│   │   └── TokenInfo.java               # Token 信息 DTO
│   ├── exception/
│   │   └── GlobalExceptionHandler.java  # 全局异常处理器
│   ├── model/
│   │   ├── Token.java                    # Token 实体
│   │   ├── TokenStatus.java              # Token 状态枚举
│   │   └── TokenStore.java               # Token 存储接口
│   ├── repository/
│   │   └── TokenRepository.java          # JPA 数据访问层
│   ├── service/
│   │   └── TokenService.java             # Token 核心服务
│   └── store/
│       ├── InMemoryTokenStore.java       # 内存存储实现
│       └── JpaTokenStore.java            # 数据库存储实现
├── src/main/resources/
│   └── application.yml                    # 应用配置
├── src/test/java/
│   └── com/example/tokenservice/
│       ├── TokenServiceIntegrationTest.java  # 集成测试
│       ├── controller/
│       │   └── TokenControllerTest.java      # 控制器测试
│       └── service/
│           └── TokenServiceTest.java         # 服务层测试
└── pom.xml
```

## 4. 核心功能

### 4.1 Token 生成
- 基于 JWT (JSON Web Token) 标准生成 Token
- 支持自定义过期时间（默认 1 小时）
- 支持自定义主题（subject）
- 自动关联用户 ID
- Token 签名使用 HS256 算法

### 4.2 Token 验证
- 验证 Token 是否有效（未过期、未作废）
- 验证 JWT 签名完整性
- 自动检测并标记过期 Token

### 4.3 Token 作废
- 将指定 Token 标记为 INVALIDATED 状态
- 作废后的 Token 无法再通过验证

### 4.4 清理过期 Token
- 批量清理已过期的 Token 记录
- 可定时调用释放存储空间

## 5. 存储机制

### 5.1 内存存储 (InMemoryTokenStore)
- 使用 `ConcurrentHashMap` 作为底层存储
- 采用 `ReadWriteLock` (读写锁) 保证线程安全
- 写操作获取写锁，读操作获取读锁
- 适合单机部署、快速访问场景

**线程安全实现要点：**
```java
private final ConcurrentHashMap<String, Token> tokenMap = new ConcurrentHashMap<>();
private final ReadWriteLock lock = new ReentrantReadWriteLock();

@Override
public Token save(Token token) {
    lock.writeLock().lock();
    try {
        tokenMap.put(token.getTokenValue(), token);
        return token;
    } finally {
        lock.writeLock().unlock();
    }
}
```

### 5.2 数据库存储 (JpaTokenStore)
- 使用 H2 嵌入式数据库
- 支持文件持久化或内存模式
- 同样使用 `ReadWriteLock` 保证线程安全
- 事务支持，保证数据一致性

### 5.3 存储切换配置
在 `application.yml` 中配置：
```yaml
token:
  storage-type: memory  # 可选: memory 或 database
```

## 6. REST API 接口

### 6.1 生成 Token
**接口地址**: `POST /api/token/generate`

**请求体**:
```json
{
    "userId": "user123",
    "subject": "login",
    "expireSeconds": 3600
}
```

**请求参数说明**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | String | 是 | 用户唯一标识 |
| subject | String | 否 | Token 主题/用途 |
| expireSeconds | Long | 否 | 过期时间（秒），默认 3600 |

**响应示例**:
```json
{
    "success": true,
    "message": "Token 生成成功",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiI..."
    }
}
```

### 6.2 验证 Token
**接口地址**: `GET /api/token/validate?token={token}`

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| token | String | 是 | 待验证的 Token |

**响应示例**:
```json
{
    "success": true,
    "message": "Token 有效",
    "data": {
        "valid": true
    }
}
```

### 6.3 获取 Token 信息
**接口地址**: `GET /api/token/info?token={token}`

**响应示例**:
```json
{
    "success": true,
    "message": "获取 Token 信息成功",
    "data": {
        "tokenValue": "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiI...",
        "userId": "user123",
        "subject": "login",
        "issuedAt": "2024-01-15T10:30:00",
        "expiresAt": "2024-01-15T11:30:00",
        "status": "ACTIVE",
        "valid": true
    }
}
```

### 6.4 作废 Token
**接口地址**: `POST /api/token/invalidate?token={token}`

**响应示例**:
```json
{
    "success": true,
    "message": "Token 作废成功",
    "data": {
        "success": true
    }
}
```

### 6.5 清理过期 Token
**接口地址**: `POST /api/token/clear-expired`

**响应示例**:
```json
{
    "success": true,
    "message": "已清理过期 Token",
    "data": null
}
```

## 7. 配置说明

### 7.1 Token 配置
```yaml
token:
  # JWT 签名密钥（必须至少 256 位）
  secret: token-service-secret-key-must-be-at-least-256-bits-long-for-hs256
  # 默认过期时间（秒）
  default-expire-seconds: 3600
  # 存储类型: memory 或 database
  storage-type: memory
```

### 7.2 数据库配置（使用 database 存储时）
```yaml
spring:
  datasource:
    # 文件模式：持久化到磁盘
    url: jdbc:h2:file:./data/tokendb;DB_CLOSE_ON_EXIT=FALSE
    # 内存模式：应用重启数据丢失
    # url: jdbc:h2:mem:tokendb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  
  h2:
    console:
      enabled: true
      path: /h2-console  # H2 控制台访问路径
  
  jpa:
    hibernate:
      ddl-auto: update  # 自动更新表结构
    show-sql: false
```

## 8. 线程安全设计

### 8.1 存储层线程安全
所有 TokenStore 实现都使用 `ReadWriteLock` 保证线程安全：

- **读操作**（findByTokenValue, existsByTokenValue）：获取读锁
- **写操作**（save, deleteByTokenValue, updateStatus, clearExpired）：获取写锁

### 8.2 状态更新的原子性
对于过期检测和状态更新，采用双重检查锁定模式：
```java
public Optional<Token> findByTokenValue(String tokenValue) {
    lock.readLock().lock();
    try {
        Token token = tokenMap.get(tokenValue);
        if (token != null && token.isExpired() && token.getStatus() == TokenStatus.ACTIVE) {
            // 升级到写锁进行状态更新
            lock.readLock().unlock();
            lock.writeLock().lock();
            try {
                // 再次检查，避免其他线程已更新
                token = tokenMap.get(tokenValue);
                if (token != null && token.isExpired() && token.getStatus() == TokenStatus.ACTIVE) {
                    token.setStatus(TokenStatus.EXPIRED);
                    tokenMap.put(tokenValue, token);
                }
            } finally {
                lock.writeLock().unlock();
            }
            lock.readLock().lock();
        }
        return Optional.ofNullable(token);
    } finally {
        lock.readLock().unlock();
    }
}
```

### 8.3 并发测试验证
项目包含多线程并发测试用例，验证：
- 并发生成 Token 的线程安全性
- 并发验证 Token 的线程安全性
- 并发作废 Token 的线程安全性（仅第一个线程成功）

## 9. 部署与运行

### 9.1 环境要求
- JDK 1.8+
- Maven 3.6+

### 9.2 编译运行
```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package

# 运行应用
java -jar target/token-service-1.0.0.jar

# 或使用 Maven 插件运行
mvn spring-boot:run
```

### 9.3 访问地址
- 服务地址: `http://localhost:8080`
- H2 控制台: `http://localhost:8080/h2-console`（仅 database 模式）

## 10. 测试说明

### 10.1 测试覆盖
- **单元测试**: TokenService, TokenController
- **集成测试**: 完整 Token 生命周期、并发场景

### 10.2 运行测试
```bash
mvn test
```

### 10.3 测试场景
1. **正常流程**: 生成 → 验证 → 作废 → 再次验证（失败）
2. **过期 Token**: 生成短期 Token → 等待过期 → 验证（失败）
3. **并发生成**: 50 个线程同时生成 Token，全部成功
4. **并发验证**: 50 个线程同时验证同一 Token，全部成功
5. **并发作废**: 10 个线程同时作废同一 Token，仅 1 个成功

## 11. 扩展建议

### 11.1 Redis 存储
可扩展实现 `RedisTokenStore`，支持分布式部署：
```java
@Component
@ConditionalOnProperty(name = "token.storage-type", havingValue = "redis")
public class RedisTokenStore implements TokenStore {
    // 使用 RedisTemplate 实现
}
```

### 11.2 定时清理
可添加定时任务自动清理过期 Token：
```java
@Scheduled(cron = "0 0 * * * ?")
public void scheduledClearExpired() {
    tokenService.clearExpiredTokens();
}
```

### 11.3 监控指标
可添加 Actuator 监控 Token 统计信息：
- 当前有效 Token 数量
- 已作废 Token 数量
- 过期 Token 数量

---

**文档版本**: v1.0  
**更新日期**: 2024-01-15
