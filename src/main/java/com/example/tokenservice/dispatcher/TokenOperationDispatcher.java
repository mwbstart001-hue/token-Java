package com.example.tokenservice.dispatcher;

import com.example.tokenservice.command.TokenCommand;
import com.example.tokenservice.command.TokenCommandType;
import com.example.tokenservice.dto.TokenInfo;
import com.example.tokenservice.factory.TokenOperationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Token 操作统一调度器
 * 调度器模式：统一调度各种 Token 操作，协调工厂、命令、策略的协作
 * 
 * 职责：
 * 1. 提供统一的操作入口
 * 2. 协调工厂创建命令
 * 3. 执行命令并返回结果
 * 4. 处理异常和日志
 * 
 * 设计模式：调度器模式（Dispatcher Pattern）
 * - 封装复杂的调用链
 * - 提供简洁的 API
 * - 便于扩展新操作
 * 
 * 协作关系：
 * ┌─────────────────────┐
 * │ 调用方 (TokenService) │
 * └──────────┬──────────┘
 *            │ 委托
 *            ▼
 * ┌─────────────────────┐
 * │ TokenOperationDispatcher │  ← 统一调度
 * └──────────┬──────────┘
 *            │
 *            ▼
 * ┌─────────────────────┐
 * │ TokenOperationFactory │  ← 工厂创建命令
 * └──────────┬──────────┘
 *            │
 *            ▼
 * ┌─────────────────────┐
 * │    TokenCommand     │  ← 命令执行
 * │ (Generate/Validate/...) │
 * └──────────┬──────────┘
 *            │
 *            ▼
 * ┌─────────────────────┐
 * │ TokenGenerator/     │  ← 策略实现
 * │   TokenValidator    │
 * └─────────────────────┘
 */
@Component
public class TokenOperationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TokenOperationDispatcher.class);

    private final TokenOperationFactory operationFactory;

    public TokenOperationDispatcher(TokenOperationFactory operationFactory) {
        this.operationFactory = operationFactory;
    }

    /**
     * 生成 Token
     * 统一调度生成 Token 的完整流程
     * 
     * @param userId 用户 ID
     * @param subject 主题（可选）
     * @param expireSeconds 过期时间（秒），null 使用默认值
     * @return 生成的 Token 字符串
     */
    public String generateToken(String userId, String subject, Long expireSeconds) {
        log.debug("调度生成 Token - userId: {}", userId);
        
        TokenCommand<String> command = operationFactory.createGenerateCommand(
            userId, subject, expireSeconds
        );
        
        return command.execute();
    }

    /**
     * 验证 Token
     * 统一调度验证 Token 的完整流程
     * 
     * @param tokenValue Token 字符串
     * @return true 有效，false 无效
     */
    public boolean validateToken(String tokenValue) {
        log.debug("调度验证 Token");
        
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.warn("验证 Token 失败：Token 为空");
            return false;
        }
        
        TokenCommand<Boolean> command = operationFactory.createValidateCommand(tokenValue);
        return command.execute();
    }

    /**
     * 续签 Token
     * 统一调度续签 Token 的完整流程
     * 
     * @param oldTokenValue 原 Token 字符串
     * @param newExpireSeconds 新的过期时间（秒）
     * @param invalidateOldToken 是否作废原 Token
     * @return 新 Token 字符串，失败返回 null
     */
    public String renewToken(String oldTokenValue, Long newExpireSeconds, boolean invalidateOldToken) {
        log.debug("调度续签 Token");
        
        if (oldTokenValue == null || oldTokenValue.trim().isEmpty()) {
            log.warn("续签 Token 失败：原 Token 为空");
            return null;
        }
        
        TokenCommand<String> command = operationFactory.createRenewCommand(
            oldTokenValue, newExpireSeconds, invalidateOldToken
        );
        
        return command.execute();
    }

    /**
     * 作废 Token
     * 统一调度作废 Token 的完整流程
     * 
     * @param tokenValue Token 字符串
     * @return true 作废成功，false 失败
     */
    public boolean invalidateToken(String tokenValue) {
        log.debug("调度作废 Token");
        
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.warn("作废 Token 失败：Token 为空");
            return false;
        }
        
        TokenCommand<Boolean> command = operationFactory.createInvalidateCommand(tokenValue);
        return command.execute();
    }

    /**
     * 获取 Token 信息
     * 统一调度获取 Token 信息的完整流程
     * 
     * @param tokenValue Token 字符串
     * @return Token 信息 Optional
     */
    @SuppressWarnings("unchecked")
    public Optional<TokenInfo> getTokenInfo(String tokenValue) {
        log.debug("调度获取 Token 信息");
        
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.warn("获取 Token 信息失败：Token 为空");
            return Optional.empty();
        }
        
        TokenCommand<?> command = operationFactory.createGetInfoCommand(tokenValue);
        Object result = command.execute();
        
        if (result instanceof Optional) {
            return (Optional<TokenInfo>) result;
        }
        
        return Optional.empty();
    }

    /**
     * 通用命令执行方法
     * 支持动态执行任意类型的命令
     * 
     * @param type 命令类型
     * @param params 命令参数
     * @param <R> 返回类型
     * @return 执行结果
     */
    @SuppressWarnings("unchecked")
    public <R> R executeCommand(TokenCommandType type, Object... params) {
        log.debug("调度执行命令: {}", type);
        
        TokenCommand<?> command = operationFactory.createCommand(type, params);
        return (R) command.execute();
    }
}
