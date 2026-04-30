package com.example.tokenservice.strategy;

import com.example.tokenservice.config.TokenProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Token 生成策略
 * 使用 @Scope("prototype") 每次获取都创建新实例，避免单例模式下的并发安全隐患
 */
@Component("simpleTokenGenerator")
@Scope("prototype")
public class SimpleTokenGenerator implements TokenGenerator {

    private static final Logger log = LoggerFactory.getLogger(SimpleTokenGenerator.class);

    private final TokenProperties tokenProperties;

    public SimpleTokenGenerator(TokenProperties tokenProperties) {
        this.tokenProperties = tokenProperties;
    }

    @Override
    public TokenGenerationResult generate(String userId, String subject,
                                            LocalDateTime issuedAt, LocalDateTime expiresAt) {
        String jwtId = UUID.randomUUID().toString();
        log.debug("生成 Simple Token - userId: {}, jwtId: {}", userId, jwtId);

        String prefix = tokenProperties.getStrategy().getSimplePrefix();
        String tokenValue = buildSimpleToken(prefix, jwtId, userId, subject, issuedAt, expiresAt);

        return new TokenGenerationResult(tokenValue, jwtId);
    }

    @Override
    public String getAlgorithm() {
        return "SIMPLE";
    }

    private String buildSimpleToken(String prefix, String jwtId, String userId, String subject,
                                     LocalDateTime issuedAt, LocalDateTime expiresAt) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        sb.append(jwtId, 0, 8);
        sb.append("-");

        long issuedTime = Date.from(issuedAt.atZone(ZoneId.systemDefault()).toInstant()).getTime();
        long expireTime = Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()).getTime();

        String payload = jwtId + "|" + userId + "|" + (subject != null ? subject : "") + "|" + issuedTime + "|" + expireTime;
        String encodedPayload = Base64.getEncoder().encodeToString(payload.getBytes());

        sb.append(encodedPayload);

        String signature = generateSignature(payload, jwtId);
        sb.append("-").append(signature);

        log.debug("Simple Token 构建完成: prefix={}, payloadLength={}", prefix, encodedPayload.length());

        return sb.toString();
    }

    private String generateSignature(String payload, String jwtId) {
        String secret = tokenProperties.getSecret() != null ? tokenProperties.getSecret() : "simple-secret-key";
        int hash = (payload + secret + jwtId).hashCode();
        return String.format("%08x", Math.abs(hash));
    }
}
