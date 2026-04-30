package com.example.tokenservice.strategy;

import com.example.tokenservice.config.JwtKeyManager;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.security.PublicKey;

/**
 * RS256 非对称加密 Token 验证策略
 * 策略模式：专门使用 RS256 (RSA-SHA256) 算法验证 Token
 * 
 * 特点：
 * - 使用公钥验证签名
 * - 安全性更高，公钥可以公开分发
 * 
 * 设计模式：策略模式 + 原型作用域
 * 
 * 使用 @Scope("prototype") 每次获取都创建新实例，避免单例模式下的并发安全隐患
 * 使用 Bean 名称 "rs256TokenValidator" 便于动态获取
 */
@Component("rs256TokenValidator")
@Scope("prototype")
public class RS256TokenValidator implements TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(RS256TokenValidator.class);

    private final JwtKeyManager jwtKeyManager;

    public RS256TokenValidator(JwtKeyManager jwtKeyManager) {
        this.jwtKeyManager = jwtKeyManager;
        log.info("初始化 RS256 非对称加密 Token 验证策略（默认）");
    }

    @Override
    public ValidationResult validate(String tokenValue) {
        log.debug("RS256 验证 Token");

        try {
            Jws<Claims> jws = parseClaims(tokenValue);
            return ValidationResult.valid(jws.getBody());
        } catch (ExpiredJwtException e) {
            log.debug("RS256 Token 已过期");
            return ValidationResult.expired(e.getClaims());
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.debug("RS256 Token 签名无效");
            return ValidationResult.invalid(ValidationStatus.INVALID_SIGNATURE, "签名无效");
        } catch (MalformedJwtException e) {
            log.debug("RS256 Token 格式错误");
            return ValidationResult.invalid(ValidationStatus.MALFORMED, "格式错误");
        } catch (UnsupportedJwtException e) {
            log.debug("RS256 不支持的 Token 类型");
            return ValidationResult.invalid(ValidationStatus.UNSUPPORTED, "不支持的类型");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("RS256 Token 验证失败: {}", e.getMessage());
            return ValidationResult.invalid(ValidationStatus.INVALID, e.getMessage());
        }
    }

    @Override
    public Claims parseQuietly(String tokenValue) {
        return jwtKeyManager.parseClaimsQuietly(tokenValue);
    }

    @Override
    public String extractUserId(String tokenValue) {
        return jwtKeyManager.extractUserIdQuietly(tokenValue);
    }

    @Override
    public String extractJwtId(String tokenValue) {
        return jwtKeyManager.extractJwtIdQuietly(tokenValue);
    }

    /**
     * 使用 RS256 算法解析并验证 Token
     * 专门使用公钥验证
     */
    private Jws<Claims> parseClaims(String tokenValue) {
        PublicKey publicKey = jwtKeyManager.getPublicKey();

        if (publicKey == null) {
            throw new IllegalStateException("RS256 算法需要公钥，但公钥未初始化");
        }

        JwtParserBuilder parserBuilder = Jwts.parserBuilder()
                .setSigningKey(publicKey);

        return parserBuilder.build().parseClaimsJws(tokenValue);
    }
}
