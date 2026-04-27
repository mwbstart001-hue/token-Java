package com.example.tokenservice.strategy;

/**
 * Token 生成结果
 * 封装生成的 Token 值和 JWT ID
 */
public class TokenGenerationResult {

    private final String tokenValue;
    private final String jwtId;

    public TokenGenerationResult(String tokenValue, String jwtId) {
        this.tokenValue = tokenValue;
        this.jwtId = jwtId;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public String getJwtId() {
        return jwtId;
    }
}
