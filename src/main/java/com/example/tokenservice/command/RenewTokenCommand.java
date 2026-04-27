package com.example.tokenservice.command;

import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import com.example.tokenservice.strategy.TokenGenerator;
import com.example.tokenservice.strategy.TokenGenerationResult;
import com.example.tokenservice.strategy.TokenValidator;
import com.example.tokenservice.strategy.TokenValidator.ValidationResult;
import com.example.tokenservice.strategy.TokenValidator.ValidationStatus;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 续签 Token 命令
 * 命令模式：封装 Token 续签的完整逻辑
 * 
 * 职责：
 * 1. 验证原 Token 有效性
 * 2. 使用 TokenGenerator 生成新 Token
 * 3. 保存新 Token
 * 4. 可选：作废原 Token
 */
public class RenewTokenCommand implements TokenCommand<String> {

    private static final Logger log = LoggerFactory.getLogger(RenewTokenCommand.class);

    private final String oldTokenValue;
    private final Long newExpireSeconds;
    private final boolean invalidateOldToken;
    private final TokenProperties tokenProperties;
    private final TokenStore tokenStore;
    private final TokenGenerator tokenGenerator;
    private final TokenValidator tokenValidator;

    public RenewTokenCommand(String oldTokenValue, Long newExpireSeconds, boolean invalidateOldToken,
                              TokenProperties tokenProperties, TokenStore tokenStore,
                              TokenGenerator tokenGenerator, TokenValidator tokenValidator) {
        this.oldTokenValue = oldTokenValue;
        this.newExpireSeconds = newExpireSeconds;
        this.invalidateOldToken = invalidateOldToken;
        this.tokenProperties = tokenProperties;
        this.tokenStore = tokenStore;
        this.tokenGenerator = tokenGenerator;
        this.tokenValidator = tokenValidator;
    }

    @Override
    public String execute() {
        log.info("开始续签 Token");

        Optional<Token> oldTokenOpt = tokenStore.findByTokenValue(oldTokenValue);

        if (!oldTokenOpt.isPresent()) {
            log.warn("续签 Token 失败：原 Token 不存在");
            return null;
        }

        Token oldToken = oldTokenOpt.get();

        if (!oldToken.isValid()) {
            log.warn("续签 Token 失败：原 Token 状态无效 - status: {}, expired: {}", 
                    oldToken.getStatus(), oldToken.isExpired());
            return null;
        }

        ValidationResult result = tokenValidator.validate(oldTokenValue);

        if (result.isValid() || result.getStatus() == ValidationStatus.EXPIRED) {
            String userId = oldToken.getUserId();
            String subject = oldToken.getSubject();

            LocalDateTime now = LocalDateTime.now();
            long actualExpireSeconds = calculateExpireSeconds(newExpireSeconds);
            LocalDateTime expiresAt = now.plusSeconds(actualExpireSeconds);

            TokenGenerationResult genResult = tokenGenerator.generate(userId, subject, now, expiresAt);
            String newTokenValue = genResult.getTokenValue();
            String newJwtId = genResult.getJwtId();

            log.debug("新 Token JWT ID: {}", newJwtId);

            Token newToken = createTokenEntity(newTokenValue, userId, subject, now, expiresAt);
            tokenStore.save(newToken);

            if (invalidateOldToken) {
                tokenStore.updateStatus(oldTokenValue, TokenStatus.INVALIDATED);
                log.debug("已作废原 Token");
            }

            log.info("Token 续签成功 - userId: {}, newJwtId: {}, expiresAt: {}", userId, newJwtId, expiresAt);
            return newTokenValue;
        } else {
            log.warn("续签 Token 失败：原 Token 验证失败 - status: {}, message: {}", 
                    result.getStatus(), result.getMessage());
            return null;
        }
    }

    @Override
    public TokenCommandType getType() {
        return TokenCommandType.RENEW;
    }

    private long calculateExpireSeconds(Long expireSeconds) {
        if (expireSeconds == null) {
            return tokenProperties.getDefaultExpireSeconds();
        }
        if (expireSeconds <= 0) {
            log.warn("过期时间为负数或零，使用默认值: {}秒", tokenProperties.getDefaultExpireSeconds());
            return tokenProperties.getDefaultExpireSeconds();
        }
        long maxExpireSeconds = tokenProperties.getMaxExpireSeconds();
        if (expireSeconds > maxExpireSeconds) {
            log.warn("过期时间 {} 超过最大值 {}，将被限制为最大值", expireSeconds, maxExpireSeconds);
            return maxExpireSeconds;
        }
        return expireSeconds;
    }

    private Token createTokenEntity(String tokenValue, String userId, String subject,
                                      LocalDateTime issuedAt, LocalDateTime expiresAt) {
        Token token = new Token();
        token.setTokenValue(tokenValue);
        token.setUserId(userId);
        token.setSubject(subject);
        token.setIssuedAt(issuedAt);
        token.setExpiresAt(expiresAt);
        token.setStatus(TokenStatus.ACTIVE);
        return token;
    }
}
