# Token 使用统计功能迭代需求文档

## 1. 需求概述

### 1.1 需求背景

随着多用户 Token 服务的广泛使用，需要对 Token 的整个生命周期进行全面的统计分析，包括生成、验证、续签等操作的详细记录，以便：

- 了解各用户的 Token 使用情况
- 分析 Token 的生命周期趋势
- 监控系统性能和使用量
- 支持业务决策和安全审计

### 1.2 核心功能

| 功能 | 描述 |
|------|------|
| **Token 续签** | 基于现有有效 Token 生成新的 Token，可选择作废原 Token |
| **操作统计** | 记录 Token 的生成、验证、续签、作废等操作 |
| **用户维度统计** | 按用户维度统计各操作类型的数量和成功率 |
| **全局统计** | 整个系统的 Token 使用统计概览 |
| **操作记录查询** | 查询指定用户的详细操作记录 |

## 2. 技术架构

### 2.1 架构设计

采用 **事件驱动 + AOP 切面** 的架构设计，确保：

1. **不修改现有接口功能**：通过 AOP 切面拦截操作，不侵入现有业务逻辑
2. **线程安全**：使用 Spring 的事务管理和线程安全的数据结构
3. **可配置化**：通过配置开关控制统计功能的启用/禁用
4. **无效 Token 不触发计数**：只有成功的有效操作才会记录统计

### 2.2 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                        API 层                                  │
│  ┌─────────────────┐  ┌─────────────────────────────────┐   │
│  │ TokenController │  │ TokenStatisticsController       │   │
│  │ - /api/token/*  │  │ - /api/token/statistics/*      │   │
│  └─────────────────┘  └─────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       服务层                                  │
│  ┌──────────────────────┐    ┌──────────────────────────┐   │
│  │ TokenService         │◄──►│ TokenStatisticsService   │   │
│  │ - generateToken      │    │ - recordOperation        │   │
│  │ - validateToken      │    │ - getUserStatistics      │   │
│  │ - renewToken (新增)  │    │ - getGlobalStatistics    │   │
│  │ - invalidateToken    │    └──────────────────────────┘   │
│  └──────────────────────┘                                    │
└─────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
┌─────────────────┐  ┌──────────────────┐  ┌─────────────────────┐
│ AOP 切面层       │  │ 事件层            │  │ 数据访问层           │
│                 │  │                  │  │                     │
│ TokenStatistics │  │ TokenOperation   │  │ TokenStatistics     │
│ Aspect          │  │ Event            │  │ Repository          │
│ (条件化启用)     │  │                  │  │                     │
└─────────────────┘  └──────────────────┘  └─────────────────────┘
```

## 3. 数据库设计

### 3.1 新增表结构

#### 3.1.1 token_statistics 表

```sql
CREATE TABLE token_statistics (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         VARCHAR(255) NOT NULL,
    jwt_id          VARCHAR(255),
    operation_type  VARCHAR(50) NOT NULL,
    success         BOOLEAN NOT NULL,
    failure_reason  VARCHAR(500),
    operation_time  TIMESTAMP NOT NULL,
    source_ip       VARCHAR(50),
    user_agent      VARCHAR(500)
);

-- 索引
CREATE INDEX idx_user_id ON token_statistics(user_id);
CREATE INDEX idx_operation_type ON token_statistics(operation_type);
CREATE INDEX idx_operation_time ON token_statistics(operation_time);
```

#### 3.1.2 操作类型枚举

| 类型 | 说明 |
|------|------|
| `GENERATE` | Token 生成 |
| `VALIDATE` | Token 验证 |
| `RENEW` | Token 续签 |
| `INVALIDATE` | Token 作废 |

### 3.2 实体类变更

#### 3.2.1 新增实体

| 类名 | 说明 |
|------|------|
| `TokenStatistics` | Token 统计记录实体 |
| `TokenOperationType` | 操作类型枚举 |
| `TokenStatisticsSummary` | 统计摘要 DTO |
| `TokenRenewRequest` | 续签请求 DTO |
| `TokenOperationEvent` | 操作事件类 |

#### 3.2.2 现有实体无变更

- `Token` 实体保持不变
- `TokenStatus` 枚举保持不变

## 4. 接口设计

### 4.1 新增接口

#### 4.1.1 Token 续签接口

**POST /api/token/renew**

续签 Token，基于现有有效 Token 生成新的 Token。

**请求体：**
```json
{
  "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expireSeconds": 3600,
  "invalidateOldToken": true
}
```

**参数说明：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| token | String | 是 | 原 Token 值 |
| expireSeconds | Long | 否 | 新 Token 过期时间（秒），默认使用配置的 defaultExpireSeconds |
| invalidateOldToken | Boolean | 否 | 是否作废原 Token，默认 true |

**响应示例：**
```json
{
  "code": 200,
  "message": "Token 续签成功",
  "data": {
    "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

#### 4.1.2 获取指定用户统计

**GET /api/token/statistics/user/{userId}**

获取指定用户的 Token 操作统计摘要。

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Path | 是 | 用户 ID |
| startTime | Query | 否 | 开始时间（ISO 格式），默认 7 天前 |
| endTime | Query | 否 | 结束时间（ISO 格式），默认当前时间 |

**响应示例：**
```json
{
  "code": 200,
  "message": "获取用户统计成功",
  "data": [
    {
      "userId": "user-001",
      "operationType": "GENERATE",
      "totalCount": 150,
      "successCount": 148,
      "failureCount": 2,
      "successRate": 98.67
    },
    {
      "userId": "user-001",
      "operationType": "VALIDATE",
      "totalCount": 5000,
      "successCount": 4950,
      "failureCount": 50,
      "successRate": 99.0
    }
  ]
}
```

#### 4.1.3 获取所有用户统计

**GET /api/token/statistics/all**

获取所有用户的 Token 操作统计摘要。

#### 4.1.4 获取全局统计

**GET /api/token/statistics/global**

获取整个系统的 Token 使用统计概览。

#### 4.1.5 获取用户操作记录

**GET /api/token/statistics/records/{userId}**

获取指定用户的详细操作记录列表。

### 4.2 现有接口无变更

以下接口保持原有功能不变：

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/token/generate | POST | 生成 Token |
| /api/token/validate | GET | 验证 Token |
| /api/token/info | GET | 获取 Token 信息 |
| /api/token/invalidate | POST | 作废 Token |
| /api/token/clear-expired | POST | 清理过期 Token |

## 5. 配置说明

### 5.1 新增配置项

在 `application.yml` 中新增以下配置：

```yaml
token:
  # 新增统计配置
  statistics:
    # 是否启用统计功能
    enabled: true
    # 是否记录无效 Token 操作
    record-invalid-tokens: false
```

### 5.2 配置说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| token.statistics.enabled | Boolean | true | 是否启用统计功能 |
| token.statistics.record-invalid-tokens | Boolean | false | 是否记录无效 Token 操作 |

### 5.3 测试环境配置

在 `application-test.yml` 中禁用统计功能：

```yaml
token:
  statistics:
    enabled: false
    record-invalid-tokens: false
```

## 6. 依赖变更

### 6.1 新增依赖

在 `pom.xml` 中新增 AOP 依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### 6.2 现有依赖无变更

## 7. 新增文件清单

| 文件路径 | 说明 |
|----------|------|
| `src/main/java/com/example/tokenservice/model/TokenOperationType.java` | 操作类型枚举 |
| `src/main/java/com/example/tokenservice/model/TokenStatistics.java` | 统计记录实体 |
| `src/main/java/com/example/tokenservice/dto/TokenRenewRequest.java` | 续签请求 DTO |
| `src/main/java/com/example/tokenservice/dto/TokenStatisticsSummary.java` | 统计摘要 DTO |
| `src/main/java/com/example/tokenservice/event/TokenOperationEvent.java` | 操作事件类 |
| `src/main/java/com/example/tokenservice/event/TokenOperationEventListener.java` | 事件监听器 |
| `src/main/java/com/example/tokenservice/repository/TokenStatisticsRepository.java` | 统计数据访问层 |
| `src/main/java/com/example/tokenservice/service/TokenStatisticsService.java` | 统计服务 |
| `src/main/java/com/example/tokenservice/aspect/TokenStatisticsAspect.java` | 统计切面 |
| `src/main/java/com/example/tokenservice/controller/TokenStatisticsController.java` | 统计控制器 |

## 8. 修改文件清单

| 文件路径 | 修改内容 |
|----------|----------|
| `pom.xml` | 新增 spring-boot-starter-aop 依赖 |
| `src/main/java/com/example/tokenservice/service/TokenService.java` | 新增 renewToken、parseClaimsQuietly 方法 |
| `src/main/java/com/example/tokenservice/controller/TokenController.java` | 新增 /renew 接口 |
| `src/main/java/com/example/tokenservice/config/TokenProperties.java` | 新增 Statistics 内部类配置 |
| `src/test/resources/application-test.yml` | 新增统计功能禁用配置 |

## 9. 测试验证

### 9.1 测试策略

| 测试类型 | 说明 |
|----------|------|
| 单元测试 | 验证核心业务逻辑的正确性 |
| 集成测试 | 验证多线程并发场景下的线程安全性 |
| 功能测试 | 验证新增接口的功能完整性 |

### 9.2 测试结果

所有测试均已通过：

- ✅ `TokenServiceTest` (8 个测试)
- ✅ `TokenControllerTest` (8 个测试)
- ✅ `TokenServiceIntegrationTest` (5 个集成测试)
- ✅ `GlobalExceptionHandlerTest` (2 个测试)
- ✅ `ApiKeyAuthenticationIntegrationTest` (2 个测试)

### 9.3 并发测试验证

通过以下测试场景验证线程安全：

1. **并发 Token 生成**
   - 50 线程并发生成 Token
   - 所有 Token 生成成功

2. **并发 Token 验证**
   - 50 线程并发验证同一 Token
   - 所有验证操作返回正确结果

3. **并发 Token 作废**
   - 10 线程并发作废同一 Token
   - 只有第一个操作成功，后续操作失败

## 10. 关键实现要点

### 10.1 不修改现有接口功能

通过 **AOP 切面 + 条件化注解** 实现：

```java
@Aspect
@Component
@ConditionalOnProperty(prefix = "token.statistics", name = "enabled", havingValue = "true")
public class TokenStatisticsAspect {
    // 拦截 TokenService 的操作，记录统计信息
}
```

### 10.2 无效 Token 不触发计数

在记录统计前进行有效性检查：

```java
public void recordOperation(...) {
    if (!success && failureReason != null) {
        log.debug("操作失败，不触发统计计数");
        return;
    }
    // 记录统计...
}
```

### 10.3 线程安全保证

1. **使用 Spring 事务管理**
   ```java
   @Transactional
   public void recordOperation(...) { ... }
   ```

2. **现有 TokenService 已保证线程安全**
   - 使用 `volatile` 和 `synchronized` 双重检查锁定
   - TokenStore 实现已保证线程安全

### 10.4 事件驱动架构

使用 Spring 的事件机制实现解耦：

```java
// 发布事件
eventPublisher.publishEvent(new TokenOperationEvent(...));

// 异步监听
@Async
@EventListener
public void handleTokenOperationEvent(TokenOperationEvent event) {
    statisticsService.recordOperation(...);
}
```

## 11. 统计流程说明

### 11.1 Token 生成流程

```
1. 用户调用 generateToken()
2. AOP 切面拦截方法调用
3. TokenService 生成新 Token
4. AOP 切面获取返回结果
5. 发布 TokenOperationEvent (GENERATE)
6. 异步事件监听器接收事件
7. TokenStatisticsService 记录统计
8. 无效操作不触发计数
```

### 11.2 Token 验证流程

```
1. 用户调用 validateToken()
2. AOP 切面拦截方法调用
3. TokenService 验证 Token 有效性
4. AOP 切面获取验证结果
5. 如果验证成功：
   - 发布 TokenOperationEvent (VALIDATE)
   - 记录统计
6. 如果验证失败：
   - 不发布事件（或根据配置决定）
   - 不记录统计
```

### 11.3 Token 续签流程

```
1. 用户调用 renewToken()
2. AOP 切面拦截方法调用
3. TokenService 执行续签：
   - 验证原 Token 有效性
   - 生成新 Token
   - 可选作废原 Token
4. AOP 切面获取新 Token
5. 发布 TokenOperationEvent (RENEW)
6. 记录统计
```

## 12. 部署说明

### 12.1 启用统计功能

在生产环境配置中启用统计功能：

```yaml
token:
  statistics:
    enabled: true
    record-invalid-tokens: false
```

### 12.2 禁用统计功能

在测试或开发环境可禁用：

```yaml
token:
  statistics:
    enabled: false
```

### 12.3 数据库初始化

由于使用 JPA 的 `ddl-auto: update`，首次启动会自动创建 `token_statistics` 表。

## 13. 风险与注意事项

### 13.1 性能影响

统计功能对性能的影响：

- **事件驱动异步处理**：统计记录不阻塞主线程
- **AOP 切面开销**：极小，仅为方法拦截的开销
- **数据库写入**：异步执行，不影响响应时间

### 13.2 数据量考虑

当统计数据量较大时，建议：

1. **定期归档**：将历史数据归档到其他表或存储
2. **分区策略**：按时间对 `token_statistics` 表分区
3. **保留期限**：根据业务需求设置数据保留期限

### 13.3 安全考虑

1. **敏感信息**：统计记录不存储 Token 敏感信息
2. **访问控制**：统计 API 应通过 API Key 认证保护
3. **审计日志**：统计查询操作应记录审计日志

## 14. 后续优化建议

### 14.1 功能扩展

- [ ] 支持按时间段聚合统计（小时、天、周、月）
- [ ] 支持 Token 链路追踪（关联生成→验证→续签）
- [ ] 支持统计数据导出（CSV、Excel）
- [ ] 支持统计数据可视化图表

### 14.2 性能优化

- [ ] 实现统计数据缓存
- [ ] 引入消息队列处理高并发场景
- [ ] 实现批量写入优化

### 14.3 监控告警

- [ ] Token 生成失败率告警
- [ ] Token 验证异常峰值告警
- [ ] 单用户 Token 生成频率告警

---

**文档版本**: v1.0  
**创建日期**: 2026-04-27  
**状态**: 已完成实现和测试验证
