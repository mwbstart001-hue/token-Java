package com.example.tokenservice.strategy;

import io.jsonwebtoken.Claims;

/**
 * Token 验证策略接口
 * 策略模式：封装 Token 验证算法
 * 
 * 职责：
 * 1. 定义 Token 验证的标准接口
 * 2. 解析并验证 JWT 签名和有效期
 * 3. 提取 Claims 信息
 */
public interface TokenValidator {

    /**
     * 验证 Token 有效性
     * 会验证签名、有效期等
     * 
     * @param tokenValue Token 字符串
     * @return 验证结果
     */
    ValidationResult validate(String tokenValue);

    /**
     * 静默解析 Token（不抛出异常）
     * 用于提取已过期 Token 的信息
     */
    Claims parseQuietly(String tokenValue);

    /**
     * 从 Token 提取 userId
     */
    String extractUserId(String tokenValue);

    /**
     * 从 Token 提取 jwtId
     */
    String extractJwtId(String tokenValue);

    /**
     * 验证结果类型
     */
    enum ValidationStatus {
        VALID,
        EXPIRED,
        INVALID_SIGNATURE,
        MALFORMED,
        UNSUPPORTED,
        INVALID
    }

    /**
     * 验证结果
     */
    class ValidationResult {
        private final boolean valid;
        private final ValidationStatus status;
        private final Claims claims;
        private final String message;

        public ValidationResult(boolean valid, ValidationStatus status, Claims claims, String message) {
            this.valid = valid;
            this.status = status;
            this.claims = claims;
            this.message = message;
        }

        public static ValidationResult valid(Claims claims) {
            return new ValidationResult(true, ValidationStatus.VALID, claims, "Token 有效");
        }

        public static ValidationResult expired(Claims claims) {
            return new ValidationResult(false, ValidationStatus.EXPIRED, claims, "Token 已过期");
        }

        public static ValidationResult invalid(ValidationStatus status, String message) {
            return new ValidationResult(false, status, null, message);
        }

        public boolean isValid() {
            return valid;
        }

        public ValidationStatus getStatus() {
            return status;
        }

        public Claims getClaims() {
            return claims;
        }

        public String getMessage() {
            return message;
        }
    }
}
