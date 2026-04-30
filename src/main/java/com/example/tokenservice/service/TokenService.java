package com.example.tokenservice.service;

import com.example.tokenservice.config.JwtKeyManager;
import com.example.tokenservice.dispatcher.TokenOperationDispatcher;
import com.example.tokenservice.dto.BatchGenerateResponse;
import com.example.tokenservice.dto.TokenInfo;
import com.example.tokenservice.model.TokenStore;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Token核心服务类
 * 提供Token的生成、验证、查询、作废等核心功能
 * 
 * 重构说明：
 * 1. 使用设计模式重构：
 *    - 策略模式：TokenGenerator/TokenValidator 封装JWT生成/验证
 *    - 命令模式：GenerateTokenCommand/ValidateTokenCommand 等封装操作
 *    - 工厂模式：TokenOperationFactory 创建命令对象
 *    - 调度器模式：TokenOperationDispatcher 统一调度
 * 2. TokenService 现在作为 API 兼容层，业务逻辑委托给调度器
 * 3. 保持公开 API 不变，所有现有测试直接兼容
 * 4. 密钥管理和JWT解析能力仍保留在 JwtKeyManager（切面依赖）
 * 
 * 架构图：
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ 调用方 (Controller/Test) - 公开 API 完全兼容                      │
 * │ ┌─────────────────────────────────────────────────────────────┐ │
 * │ │ TokenService (API 兼容层)                                    │ │
 * │ │ - 保持公开方法签名不变                                         │ │
 * │ │ - 业务逻辑委托给调度器                                         │ │
 * │ └───────────────────────┬─────────────────────────────────────┘ │
 * └─────────────────────────┼─────────────────────────────────────────┘
 *                           │ 委托
 *                           ▼
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ TokenOperationDispatcher (调度器层)                              │
 * │ - 统一调度各种操作                                                │
 * │ - 协调工厂、命令、策略                                            │
 * └───────────────────────┬─────────────────────────────────────────┘
 *                           │
 *           ┌───────────────┼───────────────┐
 *           ▼               ▼               ▼
 * ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
 * │    工厂模式      │ │    命令模式      │ │    策略模式      │
 * │ TokenOperation  │ │  TokenCommand   │ │ TokenGenerator  │
 * │    Factory      │ │                 │ │ TokenValidator  │
 * └─────────────────┘ └─────────────────┘ └─────────────────┘
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final TokenOperationDispatcher dispatcher;
    private final TokenStore tokenStore;
    private final JwtKeyManager jwtKeyManager;

    public TokenService(TokenOperationDispatcher dispatcher,
                        TokenStore tokenStore,
                        JwtKeyManager jwtKeyManager) {
        this.dispatcher = dispatcher;
        this.tokenStore = tokenStore;
        this.jwtKeyManager = jwtKeyManager;
    }

    /**
     * 生成Token
     * 
     * 重构说明：业务逻辑委托给调度器
     * - 调用 dispatcher.generateToken()
     * - 由调度器协调工厂创建命令，执行命令
     * 
     * @param userId 用户唯一标识
     * @param subject Token主题/用途（可选）
     * @param expireSeconds 过期时间（秒），null则使用默认值
     * @return 生成的JWT Token字符串
     */
    public String generateToken(String userId, String subject, Long expireSeconds) {
        log.info("开始生成Token - userId: {}, subject: {}, expireSeconds: {}", userId, subject, expireSeconds);
        
        String tokenValue = dispatcher.generateToken(userId, subject, expireSeconds);
        
        log.info("Token生成成功 - userId: {}", userId);
        return tokenValue;
    }

    /**
     * 验证Token有效性
     * 
     * 重构说明：业务逻辑委托给调度器
     * - 调用 dispatcher.validateToken()
     * 
     * @param tokenValue 待验证的Token
     * @return true表示有效，false表示无效
     */
    public boolean validateToken(String tokenValue) {
        log.debug("开始验证Token");
        
        boolean valid = dispatcher.validateToken(tokenValue);
        
        log.debug("Token验证结果: {}", valid);
        return valid;
    }

    /**
     * 解析JWT获取Claims（不抛出异常，用于获取已过期Token的信息）
     * 
     * 保留此方法的原因：
     * 1. TokenStatisticsAspect 切面依赖此方法
     * 2. 委托给 JwtKeyManager，保持解耦
     * 3. 切面不依赖业务层，只依赖基础层
     * 
     * @param tokenValue Token字符串
     * @return Claims对象，如果解析失败返回null
     */
    public Claims parseClaimsQuietly(String tokenValue) {
        return jwtKeyManager.parseClaimsQuietly(tokenValue);
    }

    /**
     * 续签Token
     * 
     * 重构说明：业务逻辑委托给调度器
     * - 调用 dispatcher.renewToken()
     * 
     * @param oldTokenValue 原Token值
     * @param newExpireSeconds 新的过期时间（秒），null则使用默认值
     * @param invalidateOldToken 是否作废原Token
     * @return 新生成的Token值，如果原Token无效则返回null
     */
    public String renewToken(String oldTokenValue, Long newExpireSeconds, boolean invalidateOldToken) {
        log.info("开始续签Token");
        
        String newTokenValue = dispatcher.renewToken(oldTokenValue, newExpireSeconds, invalidateOldToken);
        
        if (newTokenValue != null) {
            log.info("Token续签成功");
        } else {
            log.warn("Token续签失败");
        }
        return newTokenValue;
    }

    /**
     * 获取Token详细信息
     * 
     * 重构说明：业务逻辑委托给调度器
     * - 调用 dispatcher.getTokenInfo()
     * 
     * @param tokenValue Token字符串
     * @return Token信息，如果不存在则返回Optional.empty()
     */
    public Optional<TokenInfo> getTokenInfo(String tokenValue) {
        log.debug("获取Token信息");
        
        Optional<TokenInfo> info = dispatcher.getTokenInfo(tokenValue);
        
        log.debug("获取Token信息结果: {}", info.isPresent() ? "存在" : "不存在");
        return info;
    }

    /**
     * 作废Token
     * 
     * 重构说明：业务逻辑委托给调度器
     * - 调用 dispatcher.invalidateToken()
     * 
     * @param tokenValue 待作废的Token
     * @return true表示作废成功，false表示Token不存在或已作废
     */
    public boolean invalidateToken(String tokenValue) {
        log.info("尝试作废Token");
        
        boolean success = dispatcher.invalidateToken(tokenValue);
        
        if (success) {
            log.info("Token作废成功");
        } else {
            log.warn("Token作废失败");
        }
        return success;
    }

    /**
     * 清理过期Token
     * 从存储中删除所有已过期的Token记录
     * 
     * 重构说明：此方法较简单，直接调用 tokenStore
     * 不需要通过命令-调度器流程
     */
    public void clearExpiredTokens() {
        log.info("开始清理过期Token");
        tokenStore.clearExpired();
        log.info("过期Token清理完成");
    }

    /**
     * 批量生成Token
     * 
     * @param userId 用户唯一标识
     * @param subject Token主题/用途（可选）
     * @param expireSeconds 过期时间（秒），null则使用默认值
     * @param count 生成数量
     * @return 批量生成结果
     */
    public BatchGenerateResponse batchGenerateTokens(String userId, String subject, 
                                                      Long expireSeconds, int count) {
        log.info("开始批量生成Token - userId: {}, count: {}", userId, count);
        
        BatchGenerateResponse response = new BatchGenerateResponse();
        
        for (int i = 0; i < count; i++) {
            try {
                String token = dispatcher.generateToken(userId, subject, expireSeconds);
                response.addSuccessToken(token);
                log.debug("批量生成Token成功 - 索引: {}", i);
            } catch (Exception e) {
                log.error("批量生成Token失败 - 索引: {}, 错误: {}", i, e.getMessage());
                response.addFailure(i, e.getMessage());
            }
        }
        
        log.info("批量生成Token完成 - 成功: {}, 失败: {}", 
                response.getSuccessCount(), response.getFailureCount());
        return response;
    }

}
