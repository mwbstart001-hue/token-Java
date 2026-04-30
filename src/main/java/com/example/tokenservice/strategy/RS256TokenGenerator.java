package com.example.tokenservice.strategy;

import com.example.tokenservice.config.JwtKeyManager;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * RS256 非对称加密 Token 生成策略
 * 策略模式：专门使用 RS256 (RSA-SHA256) 算法生成 Token
 * 
 * 特点：
 * - 使用非对称密钥对，私钥签名，公钥验证
 * - 安全性更高，适合公开暴露的客户端
 * - 公钥可以公开分发，不需要保密
 * 
 * 设计模式：策略模式 + 原型作用域
 * 
 * 使用 @Scope("prototype") 每次获取都创建新实例，避免单例模式下的并发安全隐患
 * 使用 Bean 名称 "rs256TokenGenerator" 便于动态获取
 */
@Component("rs256TokenGenerator")
@Scope("prototype")
public class RS256TokenGenerator implements TokenGenerator {

    private static final Logger log = LoggerFactory.getLogger(RS256TokenGenerator.class);
    private static final String ALGORITHM_NAME = "RS256";

    private final JwtKeyManager jwtKeyManager;

    public RS256TokenGenerator(JwtKeyManager jwtKeyManager) {
        this.jwtKeyManager = jwtKeyManager;
        log.info("初始化 RS256 非对称加密 Token 生成策略（默认）");
    }

    @Override
    public TokenGenerationResult generate(String userId, String subject,
                                            LocalDateTime issuedAt, LocalDateTime expiresAt) {
        String jwtId = UUID.randomUUID().toString();
        log.debug("RS256 生成 Token - userId: {}, jwtId: {}", userId, jwtId);

        String tokenValue = buildJwt(jwtId, userId, subject, issuedAt, expiresAt);

        return new TokenGenerationResult(tokenValue, jwtId);
    }

    @Override
    public String getAlgorithm() {
        return ALGORITHM_NAME;
    }

    /**
     * 使用 RS256 算法构建 JWT
     * 专门使用私钥签名
     */
    private String buildJwt(String jwtId, String userId, String subject,
                             LocalDateTime issuedAt, LocalDateTime expiresAt) {
        PrivateKey privateKey = jwtKeyManager.getPrivateKey();

        if (privateKey == null) {
            throw new IllegalStateException("RS256 算法需要私钥，但私钥未初始化");
        }

        JwtBuilder builder = Jwts.builder()
                .setId(jwtId)
                .setSubject(subject != null ? subject : userId)
                .setIssuer("token-service")
                .setIssuedAt(Date.from(issuedAt.atZone(ZoneId.systemDefault()).toInstant()))
                .setExpiration(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()))
                .claim("userId", userId)
                .claim("algorithm", ALGORITHM_NAME);

        builder.signWith(privateKey, SignatureAlgorithm.RS256);
        log.debug("使用 RS256 非对称加密算法签名");

        return builder.compact();
    }
}
