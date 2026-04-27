package com.example.tokenservice.factory;

import com.example.tokenservice.command.*;
import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.model.TokenStore;
import com.example.tokenservice.strategy.TokenGenerator;
import com.example.tokenservice.strategy.TokenValidator;
import org.springframework.stereotype.Component;

/**
 * Token 操作命令工厂
 * 工厂模式：根据操作类型创建对应的命令对象
 * 
 * 职责：
 * 1. 封装命令对象的创建逻辑
 * 2. 统一管理命令的依赖注入
 * 3. 易于扩展新的操作类型
 * 
 * 设计模式：工厂模式（Factory Pattern）
 * - 将对象创建逻辑封装在工厂中
 * - 调用者不需要知道具体的实现类
 * - 符合开闭原则：对扩展开放，对修改关闭
 */
@Component
public class TokenOperationFactory {

    private final TokenProperties tokenProperties;
    private final TokenStore tokenStore;
    private final TokenGenerator tokenGenerator;
    private final TokenValidator tokenValidator;

    public TokenOperationFactory(TokenProperties tokenProperties,
                                  TokenStore tokenStore,
                                  TokenGenerator tokenGenerator,
                                  TokenValidator tokenValidator) {
        this.tokenProperties = tokenProperties;
        this.tokenStore = tokenStore;
        this.tokenGenerator = tokenGenerator;
        this.tokenValidator = tokenValidator;
    }

    /**
     * 创建生成 Token 的命令
     */
    public TokenCommand<String> createGenerateCommand(String userId, String subject, Long expireSeconds) {
        return new GenerateTokenCommand(
            userId, subject, expireSeconds,
            tokenProperties, tokenGenerator, tokenStore
        );
    }

    /**
     * 创建验证 Token 的命令
     */
    public TokenCommand<Boolean> createValidateCommand(String tokenValue) {
        return new ValidateTokenCommand(
            tokenValue, tokenStore, tokenValidator
        );
    }

    /**
     * 创建续签 Token 的命令
     */
    public TokenCommand<String> createRenewCommand(String oldTokenValue, Long newExpireSeconds, boolean invalidateOldToken) {
        return new RenewTokenCommand(
            oldTokenValue, newExpireSeconds, invalidateOldToken,
            tokenProperties, tokenStore, tokenGenerator, tokenValidator
        );
    }

    /**
     * 创建作废 Token 的命令
     */
    public TokenCommand<Boolean> createInvalidateCommand(String tokenValue) {
        return new InvalidateTokenCommand(
            tokenValue, tokenStore
        );
    }

    /**
     * 创建获取 Token 信息的命令
     */
    public TokenCommand<?> createGetInfoCommand(String tokenValue) {
        return new GetTokenInfoCommand(
            tokenValue, tokenStore
        );
    }

    /**
     * 根据命令类型创建命令
     * 通用工厂方法，适合需要动态创建命令的场景
     */
    public TokenCommand<?> createCommand(TokenCommandType type, Object... params) {
        switch (type) {
            case GENERATE:
                return createGenerateCommand(
                    (String) params[0],
                    (String) params[1],
                    (Long) params[2]
                );
            case VALIDATE:
                return createValidateCommand((String) params[0]);
            case RENEW:
                return createRenewCommand(
                    (String) params[0],
                    (Long) params[1],
                    (Boolean) params[2]
                );
            case INVALIDATE:
                return createInvalidateCommand((String) params[0]);
            case GET_INFO:
                return createGetInfoCommand((String) params[0]);
            default:
                throw new IllegalArgumentException("不支持的命令类型: " + type);
        }
    }
}
