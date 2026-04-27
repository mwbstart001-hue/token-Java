package com.example.tokenservice.command;

import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import com.example.tokenservice.strategy.TokenGenerator;
import com.example.tokenservice.strategy.TokenGenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * 生成 Token 命令
 * 命令模式：封装 Token 生成的完整逻辑
 * 
 * 职责：
 * 1. 计算过期时间
 * 2. 使用 TokenGenerator 生成 JWT
 * 3. 保存 Token 到存储
 * 4. 返回生成的 Token 值
 */
public class GenerateTokenCommand implements TokenCommand<String> {

    private static final Logger log = LoggerFactory.getLogger(GenerateTokenCommand.class);

    private final String userId;
    private final String subject;
    private final Long expireSeconds;
    private final TokenProperties tokenProperties;
    private final TokenGenerator tokenGenerator;
    private final TokenStore tokenStore;

    public GenerateTokenCommand(String userId, String subject, Long expireSeconds,
                                 TokenProperties tokenProperties,
                                 TokenGenerator tokenGenerator,
                                 TokenStore tokenStore) {
        this.userId = userId;
        this.subject = subject;
        this.expireSeconds = expireSeconds;
        this.tokenProperties = tokenProperties;
        this.tokenGenerator = tokenGenerator;
        this.tokenStore = tokenStore;
    }

    @Override
    public String execute() {
        log.info("开始生成 Token - userId: {}, subject: {}, expireSeconds: {}", userId, subject, expireSeconds);

        long actualExpireSeconds = calculateExpireSeconds(expireSeconds);
        log.debug("Token 过期时间: {} 秒", actualExpireSeconds);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(actualExpireSeconds);

        TokenGenerationResult result = tokenGenerator.generate(userId, subject, now, expiresAt);
        String tokenValue = result.getTokenValue();
        String jwtId = result.getJwtId();

        log.debug("Token JWT ID: {}", jwtId);

        Token token = createTokenEntity(tokenValue, userId, subject, now, expiresAt);
        tokenStore.save(token);

        log.info("Token 生成成功 - userId: {}, jwtId: {}, expiresAt: {}", userId, jwtId, expiresAt);
        return tokenValue;
    }

    @Override
    public TokenCommandType getType() {
        return TokenCommandType.GENERATE;
    }

    /**
     * 计算实际的过期时间
     * 限制最大过期时间，防止生成永不过期的 token
     */
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

    /**
     * 创建 Token 实体对象
     */
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
