package com.example.tokenservice.strategy;

import com.example.tokenservice.config.JwtKeyManager;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token 生成策略实现
 * 策略模式：使用 JJWT 库生成 JWT Token
 * 
 * 职责：
 * 1. 构建 JWT 字符串
 * 2. 处理签名算法（HS256/RS256）
 * 3. 委托给 JwtKeyManager 进行密钥管理
 */
@Component
public class JwtTokenGenerator implements TokenGenerator {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenGenerator.class);

    private final JwtKeyManager jwtKeyManager;

    public JwtTokenGenerator(JwtKeyManager jwtKeyManager) {
        this.jwtKeyManager = jwtKeyManager;
    }

    @Override
    public TokenGenerationResult generate(String userId, String subject,
                                            LocalDateTime issuedAt, LocalDateTime expiresAt) {
        String jwtId = UUID.randomUUID().toString();
        log.debug("生成 Token - userId: {}, jwtId: {}", userId, jwtId);

        String tokenValue = buildJwt(jwtId, userId, subject, issuedAt, expiresAt);

        return new TokenGenerationResult(tokenValue, jwtId);
    }

    @Override
    public String getAlgorithm() {
        return jwtKeyManager.getAlgorithm().getJcaName();
    }

    private String buildJwt(String jwtId, String userId, String subject,
                             LocalDateTime issuedAt, LocalDateTime expiresAt) {
        JwtBuilder builder = Jwts.builder()
                .setId(jwtId)
                .setSubject(subject != null ? subject : userId)
                .setIssuer("token-service")
                .setIssuedAt(Date.from(issuedAt.atZone(ZoneId.systemDefault()).toInstant()))
                .setExpiration(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()))
                .claim("userId", userId);

        builder.signWith(jwtKeyManager.getSigningKey(), jwtKeyManager.getAlgorithm());
        log.debug("使用 {} 算法签名", jwtKeyManager.getAlgorithm().getJcaName());

        return builder.compact();
    }
}
