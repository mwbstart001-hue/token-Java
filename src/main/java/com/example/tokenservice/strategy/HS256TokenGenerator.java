package com.example.tokenservice.strategy;

import com.example.tokenservice.config.JwtKeyManager;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 对称加密 Token 生成策略
 * 策略模式：专门使用 HS256 (HMAC-SHA256) 算法生成 Token
 * 
 * 特点：
 * - 使用对称密钥，同一个密钥用于签名和验证
 * - 性能较好，适合内部服务间通信
 * - 密钥需要保密，不适合公开暴露的客户端
 * 
 * 设计模式：策略模式 + 原型作用域
 * 
 * 使用 @Scope("prototype") 每次获取都创建新实例，避免单例模式下的并发安全隐患
 * 使用 Bean 名称 "hs256TokenGenerator" 便于动态获取
 */
@Component("hs256TokenGenerator")
@Scope("prototype")
public class HS256TokenGenerator implements TokenGenerator {

    private static final Logger log = LoggerFactory.getLogger(HS256TokenGenerator.class);
    private static final String ALGORITHM_NAME = "HS256";

    private final JwtKeyManager jwtKeyManager;

    public HS256TokenGenerator(JwtKeyManager jwtKeyManager) {
        this.jwtKeyManager = jwtKeyManager;
        log.info("初始化 HS256 对称加密 Token 生成策略");
    }

    @Override
    public TokenGenerationResult generate(String userId, String subject,
                                            LocalDateTime issuedAt, LocalDateTime expiresAt) {
        String jwtId = UUID.randomUUID().toString();
        log.debug("HS256 生成 Token - userId: {}, jwtId: {}", userId, jwtId);

        String tokenValue = buildJwt(jwtId, userId, subject, issuedAt, expiresAt);

        return new TokenGenerationResult(tokenValue, jwtId);
    }

    @Override
    public String getAlgorithm() {
        return ALGORITHM_NAME;
    }

    /**
     * 使用 HS256 算法构建 JWT
     * 专门使用对称密钥签名
     */
    private String buildJwt(String jwtId, String userId, String subject,
                             LocalDateTime issuedAt, LocalDateTime expiresAt) {
        SecretKey secretKey = jwtKeyManager.getSecretKey();

        JwtBuilder builder = Jwts.builder()
                .setId(jwtId)
                .setSubject(subject != null ? subject : userId)
                .setIssuer("token-service")
                .setIssuedAt(Date.from(issuedAt.atZone(ZoneId.systemDefault()).toInstant()))
                .setExpiration(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()))
                .claim("userId", userId)
                .claim("algorithm", ALGORITHM_NAME);

        builder.signWith(secretKey, SignatureAlgorithm.HS256);
        log.debug("使用 HS256 对称加密算法签名");

        return builder.compact();
    }
}
