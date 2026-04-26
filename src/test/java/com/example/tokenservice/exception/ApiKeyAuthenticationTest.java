package com.example.tokenservice.exception;

import com.example.tokenservice.config.TokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyAuthenticationTest {

    private TokenProperties tokenProperties;

    @BeforeEach
    void setUp() {
        tokenProperties = new TokenProperties();
    }

    @Test
    void whenApiKeyEnabled_ShouldRequireValidation() {
        TokenProperties.ApiKey apiKeyConfig = new TokenProperties.ApiKey();
        apiKeyConfig.setEnabled(true);
        apiKeyConfig.setHeaderName("X-API-Key");
        apiKeyConfig.setAllowedKeys(new String[]{"valid-api-key-12345"});
        tokenProperties.setApiKey(apiKeyConfig);

        assertTrue(tokenProperties.getApiKey().isEnabled());
        assertEquals("X-API-Key", tokenProperties.getApiKey().getHeaderName());
        assertArrayEquals(new String[]{"valid-api-key-12345"}, tokenProperties.getApiKey().getAllowedKeys());
    }

    @Test
    void whenApiKeyDisabled_ShouldNotRequireValidation() {
        TokenProperties.ApiKey apiKeyConfig = new TokenProperties.ApiKey();
        apiKeyConfig.setEnabled(false);
        tokenProperties.setApiKey(apiKeyConfig);

        assertFalse(tokenProperties.getApiKey().isEnabled());
    }

    @Test
    void authenticationException_WithMissingApiKey_ShouldHaveCorrectCode() {
        AuthenticationException ex = new AuthenticationException(ErrorCode.AUTH_MISSING_API_KEY);

        assertEquals(ErrorCode.AUTH_MISSING_API_KEY, ex.getErrorCode());
        assertEquals(ErrorCode.AUTH_MISSING_API_KEY.getCode(), ex.getCode());
        assertEquals("缺少API密钥", ex.getMessage());
    }

    @Test
    void authenticationException_WithInvalidApiKey_ShouldHaveCorrectCode() {
        AuthenticationException ex = new AuthenticationException(ErrorCode.AUTH_INVALID_API_KEY, "无效的API密钥");

        assertEquals(ErrorCode.AUTH_INVALID_API_KEY, ex.getErrorCode());
        assertEquals(ErrorCode.AUTH_INVALID_API_KEY.getCode(), ex.getCode());
        assertEquals("无效的API密钥", ex.getMessage());
    }

    @Test
    void authenticationException_WithDetailMessage_ShouldOverrideMessage() {
        String detailMessage = "API密钥已过期，请联系管理员获取新密钥";
        AuthenticationException ex = new AuthenticationException(ErrorCode.AUTH_FAILED, detailMessage);

        assertEquals(ErrorCode.AUTH_FAILED, ex.getErrorCode());
        assertEquals(detailMessage, ex.getDetailMessage());
        assertEquals(detailMessage, ex.getMessage());
    }
}
