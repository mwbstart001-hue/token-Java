package com.example.tokenservice.controller;

import com.example.tokenservice.common.TenantContext;
import com.example.tokenservice.config.JwtConfig;
import com.example.tokenservice.dto.*;
import com.example.tokenservice.ratelimit.RateLimitService;
import com.example.tokenservice.service.TokenService;
import com.example.tokenservice.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenControllerTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private RateLimitService rateLimitService;
    
    @Mock
    private UserService userService;

    @InjectMocks
    private TokenController tokenController;

    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void login_ShouldReturnTokenPair_WhenSuccessful() {
        TokenRequest request = new TokenRequest();
        request.setUserId("user-123");
        request.setPassword("password123");

        TokenPair expectedPair = TokenPair.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .userId("user-123")
                .accessTokenExpiresAt(LocalDateTime.now().plusHours(2))
                .refreshTokenExpiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(rateLimitService.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);
        when(userService.authenticate(eq("user-123"), eq("password123"))).thenReturn(true);
        when(userService.getUsername(eq("user-123"))).thenReturn("testuser");
        when(tokenService.generateTokenPair(eq("user-123"), eq("testuser"))).thenReturn(expectedPair);

        ApiResponse<TokenPair> response = tokenController.login(request, httpRequest);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals("access-token", response.getData().getAccessToken());
        assertEquals("refresh-token", response.getData().getRefreshToken());
    }
    
    @Test
    void login_ShouldReturnUnauthorized_WhenPasswordInvalid() {
        TokenRequest request = new TokenRequest();
        request.setUserId("user-123");
        request.setPassword("wrong-password");

        when(rateLimitService.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);
        when(userService.authenticate(eq("user-123"), eq("wrong-password"))).thenReturn(false);

        ApiResponse<TokenPair> response = tokenController.login(request, httpRequest);

        assertNotNull(response);
        assertEquals(401, response.getCode());
        assertEquals("用户名或密码错误", response.getMessage());
    }

    @Test
    void login_ShouldReturnRateLimitError_WhenRateLimitExceeded() {
        TokenRequest request = new TokenRequest();
        request.setUserId("user-123");
        request.setUsername("testuser");
        request.setPassword("password123");

        when(rateLimitService.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(false);

        ApiResponse<TokenPair> response = tokenController.login(request, httpRequest);

        assertNotNull(response);
        assertEquals(429, response.getCode());
        assertEquals("请求过于频繁，请稍后重试", response.getMessage());
    }

    @Test
    void refreshToken_ShouldReturnNewTokenPair_WhenSuccessful() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");

        TokenPair expectedPair = TokenPair.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .userId("user-123")
                .accessTokenExpiresAt(LocalDateTime.now().plusHours(2))
                .refreshTokenExpiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(rateLimitService.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);
        when(tokenService.refreshToken(eq("old-refresh-token"))).thenReturn(expectedPair);

        ApiResponse<TokenPair> response = tokenController.refreshToken(request, httpRequest);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals("new-access-token", response.getData().getAccessToken());
    }

    @Test
    void refreshToken_ShouldReturnError_WhenTokenInvalid() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("invalid-token");

        when(rateLimitService.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);
        when(tokenService.refreshToken(eq("invalid-token"))).thenThrow(new IllegalArgumentException("Refresh Token 无效"));

        ApiResponse<TokenPair> response = tokenController.refreshToken(request, httpRequest);

        assertNotNull(response);
        assertNotEquals(200, response.getCode());
    }

    @Test
    void validateToken_ShouldReturnValid_WhenTokenValid() {
        String validToken = "valid-access-token";
        ValidationResult expectedResult = ValidationResult.builder()
                .valid(true)
                .userId("user-123")
                .message("Token 有效")
                .build();

        when(rateLimitService.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);
        when(tokenService.validateToken(eq(validToken))).thenReturn(expectedResult);

        ApiResponse<ValidationResult> response = tokenController.validateToken("Bearer " + validToken, httpRequest);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertTrue(response.getData().isValid());
        assertEquals("user-123", response.getData().getUserId());
    }

    @Test
    void validateToken_ShouldReturnError_WhenTokenMissing() {
        when(rateLimitService.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);

        ApiResponse<ValidationResult> response = tokenController.validateToken(null, httpRequest);

        assertNotNull(response);
        assertNotEquals(200, response.getCode());
    }

    @Test
    void revokeToken_ShouldReturnSuccess_WhenTokenRevoked() {
        String token = "valid-token";

        when(tokenService.revokeToken(eq(token))).thenReturn(true);

        ApiResponse<Boolean> response = tokenController.revokeToken("Bearer " + token, httpRequest);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertTrue(response.getData());
    }

    @Test
    void revokeToken_ShouldReturnError_WhenTokenMissing() {
        ApiResponse<Boolean> response = tokenController.revokeToken(null, httpRequest);

        assertNotNull(response);
        assertNotEquals(200, response.getCode());
    }

    @Test
    void logout_ShouldReturnSuccess_WhenSuccessful() {
        String token = "valid-token";

        when(tokenService.revokeToken(eq(token))).thenReturn(true);

        ApiResponse<Boolean> response = tokenController.logout("Bearer " + token, httpRequest);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertTrue(response.getData());
    }
}
