package com.example.tokenservice.strategy;

import com.example.tokenservice.config.TokenProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component("simpleTokenValidator")
public class SimpleTokenValidator implements TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(SimpleTokenValidator.class);

    private final TokenProperties tokenProperties;

    public SimpleTokenValidator(TokenProperties tokenProperties) {
        this.tokenProperties = tokenProperties;
    }

    @Override
    public ValidationResult validate(String tokenValue) {
        log.debug("验证 Simple Token");

        if (tokenValue == null || tokenValue.isEmpty()) {
            return ValidationResult.invalid(ValidationStatus.MALFORMED, "Token 为空");
        }

        String prefix = tokenProperties.getStrategy().getSimplePrefix();
        if (!tokenValue.startsWith(prefix)) {
            return ValidationResult.invalid(ValidationStatus.MALFORMED, "Token 格式错误（缺少前缀）");
        }

        try {
            String[] parts = tokenValue.split("-");
            if (parts.length < 3) {
                return ValidationResult.invalid(ValidationStatus.MALFORMED, "Token 格式错误（分段不足）");
            }

            String encodedPayload = parts[1];
            String signature = parts[2];

            String payload = new String(Base64.getDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\|");

            if (payloadParts.length < 5) {
                return ValidationResult.invalid(ValidationStatus.MALFORMED, "Token 负载格式错误");
            }

            String jwtId = payloadParts[0];
            String userId = payloadParts[1];
            String subject = payloadParts[2];
            long issuedTime = Long.parseLong(payloadParts[3]);
            long expireTime = Long.parseLong(payloadParts[4]);

            String expectedSignature = generateSignature(payload, jwtId);
            if (!expectedSignature.equals(signature)) {
                return ValidationResult.invalid(ValidationStatus.INVALID_SIGNATURE, "签名无效");
            }

            long now = System.currentTimeMillis();
            if (now > expireTime) {
                Claims claims = buildClaims(jwtId, userId, subject, issuedTime, expireTime);
                return ValidationResult.expired(claims);
            }

            Claims claims = buildClaims(jwtId, userId, subject, issuedTime, expireTime);
            return ValidationResult.valid(claims);

        } catch (Exception e) {
            log.debug("Simple Token 验证失败: {}", e.getMessage());
            return ValidationResult.invalid(ValidationStatus.MALFORMED, "Token 解析失败: " + e.getMessage());
        }
    }

    @Override
    public Claims parseQuietly(String tokenValue) {
        try {
            ValidationResult result = validate(tokenValue);
            return result.getClaims();
        } catch (Exception e) {
            log.debug("解析 Simple Token Claims 失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String extractUserId(String tokenValue) {
        Claims claims = parseQuietly(tokenValue);
        if (claims != null) {
            String userId = claims.get("userId", String.class);
            if (userId != null) {
                return userId;
            }
            return claims.getSubject();
        }
        return "unknown";
    }

    @Override
    public String extractJwtId(String tokenValue) {
        Claims claims = parseQuietly(tokenValue);
        if (claims != null) {
            return claims.getId();
        }
        return null;
    }

    private String generateSignature(String payload, String jwtId) {
        String secret = tokenProperties.getSecret() != null ? tokenProperties.getSecret() : "simple-secret-key";
        int hash = (payload + secret + jwtId).hashCode();
        return String.format("%08x", Math.abs(hash));
    }

    private Claims buildClaims(String jwtId, String userId, String subject, long issuedTime, long expireTime) {
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("jti", jwtId);
        claimsMap.put("sub", subject != null && !subject.isEmpty() ? subject : userId);
        claimsMap.put("userId", userId);
        claimsMap.put("iss", "token-service");
        claimsMap.put("iat", new Date(issuedTime));
        claimsMap.put("exp", new Date(expireTime));

        return Jwts.claims(claimsMap);
    }
}
