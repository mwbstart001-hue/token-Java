package com.example.tokenservice.strategy;

import com.example.tokenservice.config.JwtKeyManager;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * HS256 对称加密 Token 验证策略
 * 策略模式：专门使用 HS256 (HMAC-SHA256) 算法验证 Token
 * 
 * 特点：
 * - 使用相同的对称密钥验证签名
 * - 性能较好
 * 
 * 使用条件：
 * - 配置 token.algorithm = "HS256"
 * 
 * 设计模式：策略模式 + 条件化 Bean
 */
@Component
@ConditionalOnProperty(name = "token.algorithm", havingValue = "HS256")
public class HS256TokenValidator implements TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(HS256TokenValidator.class);

    private final JwtKeyManager jwtKeyManager;

    public HS256TokenValidator(JwtKeyManager jwtKeyManager) {
        this.jwtKeyManager = jwtKeyManager;
        log.info("初始化 HS256 对称加密 Token 验证策略");
    }

    @Override
    public ValidationResult validate(String tokenValue) {
        log.debug("HS256 验证 Token");

        try {
            Jws<Claims> jws = parseClaims(tokenValue);
            return ValidationResult.valid(jws.getBody());
        } catch (ExpiredJwtException e) {
            log.debug("HS256 Token 已过期");
            return ValidationResult.expired(e.getClaims());
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.debug("HS256 Token 签名无效");
            return ValidationResult.invalid(ValidationStatus.INVALID_SIGNATURE, "签名无效");
        } catch (MalformedJwtException e) {
            log.debug("HS256 Token 格式错误");
            return ValidationResult.invalid(ValidationStatus.MALFORMED, "格式错误");
        } catch (UnsupportedJwtException e) {
            log.debug("HS256 不支持的 Token 类型");
            return ValidationResult.invalid(ValidationStatus.UNSUPPORTED, "不支持的类型");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("HS256 Token 验证失败: {}", e.getMessage());
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
     * 使用 HS256 算法解析并验证 Token
     * 专门使用对称密钥验证
     */
    private Jws<Claims> parseClaims(String tokenValue) {
        SecretKey secretKey = jwtKeyManager.getSecretKey();

        JwtParserBuilder parserBuilder = Jwts.parserBuilder()
                .setSigningKey(secretKey);

        return parserBuilder.build().parseClaimsJws(tokenValue);
    }
}
