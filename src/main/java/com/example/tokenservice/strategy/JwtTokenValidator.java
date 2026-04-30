package com.example.tokenservice.strategy;

import com.example.tokenservice.config.JwtKeyManager;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * JWT Token 验证策略实现（通用版本）
 * 策略模式：使用 JJWT 库验证 JWT Token
 * 
 * 职责：
 * 1. 验证 JWT 签名
 * 2. 验证有效期
 * 3. 提取 Claims 信息
 * 4. 委托给 JwtKeyManager 进行密钥管理
 * 
 * 注意：此为通用实现，建议使用专用实现 rs256TokenValidator 或 hs256TokenValidator
 * 
 * 设计模式：策略模式 + 原型作用域
 * 使用 @Scope("prototype") 每次获取都创建新实例，避免单例模式下的并发安全隐患
 * 使用 Bean 名称 "jwtTokenValidator" 便于动态获取
 */
@Component("jwtTokenValidator")
@Scope("prototype")
public class JwtTokenValidator implements TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);

    private final JwtKeyManager jwtKeyManager;

    public JwtTokenValidator(JwtKeyManager jwtKeyManager) {
        this.jwtKeyManager = jwtKeyManager;
    }

    @Override
    public ValidationResult validate(String tokenValue) {
        log.debug("验证 Token");

        try {
            Jws<Claims> jws = parseClaims(tokenValue);
            return ValidationResult.valid(jws.getBody());
        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期");
            return ValidationResult.expired(e.getClaims());
        } catch (SignatureException e) {
            log.debug("Token 签名无效");
            return ValidationResult.invalid(ValidationStatus.INVALID_SIGNATURE, "签名无效");
        } catch (MalformedJwtException e) {
            log.debug("Token 格式错误");
            return ValidationResult.invalid(ValidationStatus.MALFORMED, "格式错误");
        } catch (UnsupportedJwtException e) {
            log.debug("不支持的 Token 类型");
            return ValidationResult.invalid(ValidationStatus.UNSUPPORTED, "不支持的类型");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token 验证失败: {}", e.getMessage());
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

    private Jws<Claims> parseClaims(String tokenValue) {
        return jwtKeyManager.parseClaims(tokenValue);
    }
}
