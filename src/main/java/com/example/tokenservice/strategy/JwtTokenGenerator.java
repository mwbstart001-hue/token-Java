package com.example.tokenservice.strategy;

import com.example.tokenservice.config.JwtKeyManager;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token 生成策略实现（通用版本）
 * 策略模式：使用 JJWT 库生成 JWT Token
 * 
 * 职责：
 * 1. 构建 JWT 字符串
 * 2. 处理签名算法（HS256/RS256）- 根据 JwtKeyManager 配置
 * 3. 委托给 JwtKeyManager 进行密钥管理
 * 
 * 注意：此为通用实现，建议使用专用实现 rs256TokenGenerator 或 hs256TokenGenerator
 * 
 * 设计模式：策略模式 + 原型作用域
 * 使用 @Scope("prototype") 每次获取都创建新实例，避免单例模式下的并发安全隐患
 * 使用 Bean 名称 "jwtTokenGenerator" 便于动态获取
 */
@Component("jwtTokenGenerator")
@Scope("prototype")
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
