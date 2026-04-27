package com.example.tokenservice.strategy;

import com.example.tokenservice.config.JwtKeyManager;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * 使用条件：
 * - 配置 token.algorithm = "HS256"
 * 
 * 设计模式：策略模式 + 条件化 Bean
 */
@Component
@ConditionalOnProperty(name = "token.algorithm", havingValue = "HS256")
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
