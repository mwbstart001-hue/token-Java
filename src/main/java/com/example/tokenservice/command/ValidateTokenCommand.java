package com.example.tokenservice.command;

import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import com.example.tokenservice.strategy.TokenValidator;
import com.example.tokenservice.strategy.TokenValidator.ValidationResult;
import com.example.tokenservice.strategy.TokenValidator.ValidationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 验证 Token 命令
 * 命令模式：封装 Token 验证的完整逻辑
 * 
 * 职责：
 * 1. 检查 Token 是否存在于存储
 * 2. 检查 Token 状态
 * 3. 使用 TokenValidator 验证 JWT 签名
 * 4. 处理过期和无效情况
 */
public class ValidateTokenCommand implements TokenCommand<Boolean> {

    private static final Logger log = LoggerFactory.getLogger(ValidateTokenCommand.class);

    private final String tokenValue;
    private final TokenStore tokenStore;
    private final TokenValidator tokenValidator;

    public ValidateTokenCommand(String tokenValue, TokenStore tokenStore, TokenValidator tokenValidator) {
        this.tokenValue = tokenValue;
        this.tokenStore = tokenStore;
        this.tokenValidator = tokenValidator;
    }

    @Override
    public Boolean execute() {
        log.debug("开始验证 Token");

        Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);

        if (!tokenOpt.isPresent()) {
            log.warn("Token 不存在");
            return false;
        }

        Token token = tokenOpt.get();

        if (!token.isValid()) {
            log.warn("Token 状态无效 - status: {}, expired: {}", token.getStatus(), token.isExpired());
            return false;
        }

        ValidationResult result = tokenValidator.validate(tokenValue);

        if (result.isValid()) {
            log.debug("Token 验证成功 - userId: {}", token.getUserId());
            return true;
        } else {
            TokenStatus statusToUpdate = mapToTokenStatus(result.getStatus());
            if (statusToUpdate != null) {
                tokenStore.updateStatus(tokenValue, statusToUpdate);
            }
            log.warn("Token 验证失败 - status: {}, message: {}", result.getStatus(), result.getMessage());
            return false;
        }
    }

    @Override
    public TokenCommandType getType() {
        return TokenCommandType.VALIDATE;
    }

    private TokenStatus mapToTokenStatus(ValidationStatus status) {
        switch (status) {
            case EXPIRED:
                return TokenStatus.EXPIRED;
            case INVALID_SIGNATURE:
            case MALFORMED:
            case UNSUPPORTED:
            case INVALID:
                return TokenStatus.INVALIDATED;
            default:
                return null;
        }
    }
}
