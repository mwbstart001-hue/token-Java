package com.example.tokenservice.service;

import com.example.tokenservice.common.TenantContext;
import com.example.tokenservice.config.JwtConfig;
import com.example.tokenservice.dto.TokenPair;
import com.example.tokenservice.dto.ValidationResult;
import com.example.tokenservice.lock.DistributedLock;
import com.example.tokenservice.store.TokenStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private TokenStore tokenStore;

    @Mock
    private DistributedLock distributedLock;

    private TokenService tokenService;
    
    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-32-characters-long";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void setUp() {
        lenient().when(jwtConfig.getSecret()).thenReturn(TEST_SECRET);
        lenient().when(jwtConfig.getAccessTokenExpiration()).thenReturn(7200000L);
        lenient().when(jwtConfig.getRefreshTokenExpiration()).thenReturn(604800000L);
        
        tokenService = new TokenService(jwtConfig, tokenStore, distributedLock);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void generateTokenPair_ShouldGenerateValidTokens() {
        String userId = "user-123";
        String username = "testuser";

        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair tokenPair = tokenService.generateTokenPair(userId, username);

        assertNotNull(tokenPair);
        assertNotNull(tokenPair.getAccessToken());
        assertNotNull(tokenPair.getRefreshToken());
        assertEquals(userId, tokenPair.getUserId());
        assertNotNull(tokenPair.getAccessTokenExpiresAt());
        assertNotNull(tokenPair.getRefreshTokenExpiresAt());

        verify(tokenStore).saveAccessToken(eq(userId), anyString(), eq(7200000L));
        verify(tokenStore).saveRefreshToken(eq(userId), anyString(), eq(604800000L));
    }
    
    @Test
    void generateTokenPair_ShouldIncludeTenantIdInToken() {
        String userId = "user-123";
        String username = "testuser";
        String tenantId = "tenant-001";
        
        TenantContext.setTenantId(tenantId);

        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair tokenPair = tokenService.generateTokenPair(userId, username);

        assertNotNull(tokenPair.getAccessToken());
        
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(SIGNING_KEY)
                .build()
                .parseClaimsJws(tokenPair.getAccessToken())
                .getBody();
        
        assertEquals(tenantId, claims.get("tenantId"));
    }
    
    @Test
    void generateTokenPair_ShouldUseDefaultTenant_WhenNoTenantSet() {
        String userId = "user-123";
        String username = "testuser";

        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair tokenPair = tokenService.generateTokenPair(userId, username);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(SIGNING_KEY)
                .build()
                .parseClaimsJws(tokenPair.getAccessToken())
                .getBody();
        
        assertEquals("default", claims.get("tenantId"));
    }

    @Test
    void validateToken_ShouldReturnValid_WhenTokenIsValid() {
        String userId = "user-123";
        String username = "testuser";

        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair tokenPair = tokenService.generateTokenPair(userId, username);

        when(tokenStore.isBlacklisted(anyString())).thenReturn(false);
        when(tokenStore.validateAccessToken(eq(userId), anyString())).thenReturn(true);

        ValidationResult result = tokenService.validateToken(tokenPair.getAccessToken());

        assertTrue(result.isValid());
        assertEquals(userId, result.getUserId());
        assertEquals("Token 有效", result.getMessage());
    }
    
    @Test
    void validateToken_ShouldReturnInvalid_WhenTenantMismatch() {
        String userId = "user-123";
        String username = "testuser";
        String originalTenant = "tenant-001";
        String differentTenant = "tenant-002";
        
        TenantContext.setTenantId(originalTenant);
        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair tokenPair = tokenService.generateTokenPair(userId, username);
        
        TenantContext.clear();
        TenantContext.setTenantId(differentTenant);

        when(tokenStore.isBlacklisted(anyString())).thenReturn(false);

        ValidationResult result = tokenService.validateToken(tokenPair.getAccessToken());

        assertFalse(result.isValid());
        assertEquals("租户不匹配", result.getMessage());
    }
    
    @Test
    void validateToken_ShouldReturnValid_WhenTenantMatches() {
        String userId = "user-123";
        String username = "testuser";
        String tenantId = "tenant-001";
        
        TenantContext.setTenantId(tenantId);
        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair tokenPair = tokenService.generateTokenPair(userId, username);

        when(tokenStore.isBlacklisted(anyString())).thenReturn(false);
        when(tokenStore.validateAccessToken(eq(userId), anyString())).thenReturn(true);

        ValidationResult result = tokenService.validateToken(tokenPair.getAccessToken());

        assertTrue(result.isValid());
    }

    @Test
    void validateToken_ShouldReturnInvalid_WhenTokenIsBlacklisted() {
        String userId = "user-123";
        String username = "testuser";

        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair tokenPair = tokenService.generateTokenPair(userId, username);

        when(tokenStore.isBlacklisted(eq(tokenPair.getAccessToken()))).thenReturn(true);

        ValidationResult result = tokenService.validateToken(tokenPair.getAccessToken());

        assertFalse(result.isValid());
        assertEquals("Token 已作废", result.getMessage());
    }

    @Test
    void refreshToken_ShouldRotateTokens() throws Exception {
        String userId = "user-123";
        String username = "testuser";

        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair originalPair = tokenService.generateTokenPair(userId, username);

        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(tokenStore.isRefreshTokenUsed(anyString())).thenReturn(false);
        when(tokenStore.validateRefreshToken(eq(userId), anyString())).thenReturn(true);
        when(tokenStore.getAccessToken(eq(userId))).thenReturn(Optional.of(originalPair.getAccessToken()));
        doNothing().when(tokenStore).removeRefreshToken(anyString());
        doNothing().when(tokenStore).addToBlacklist(anyString(), anyLong());
        doNothing().when(tokenStore).markRefreshTokenUsed(anyString(), anyString(), anyString(), anyLong());
        doNothing().when(distributedLock).unlock(anyString());

        TokenPair newPair = tokenService.refreshToken(originalPair.getRefreshToken());

        assertNotNull(newPair);
        assertNotNull(newPair.getAccessToken());
        assertNotNull(newPair.getRefreshToken());

        verify(tokenStore).removeRefreshToken(eq(userId));
        verify(tokenStore).addToBlacklist(eq(originalPair.getRefreshToken()), anyLong());
        verify(tokenStore).markRefreshTokenUsed(eq(originalPair.getRefreshToken()), anyString(), eq(userId), anyLong());
    }
    
    @Test
    void refreshToken_ShouldThrowException_WhenTenantMismatch() {
        String userId = "user-123";
        String username = "testuser";
        String originalTenant = "tenant-001";
        String differentTenant = "tenant-002";
        
        TenantContext.setTenantId(originalTenant);
        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair originalPair = tokenService.generateTokenPair(userId, username);
        
        TenantContext.clear();
        TenantContext.setTenantId(differentTenant);

        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(tokenStore.isRefreshTokenUsed(anyString())).thenReturn(false);
        doNothing().when(distributedLock).unlock(anyString());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            tokenService.refreshToken(originalPair.getRefreshToken());
        });

        assertEquals("租户不匹配", exception.getMessage());
    }

    @Test
    void refreshToken_ShouldThrowException_WhenTokenIsUsed() throws Exception {
        String userId = "user-123";
        String username = "testuser";

        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair originalPair = tokenService.generateTokenPair(userId, username);

        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(tokenStore.isRefreshTokenUsed(eq(originalPair.getRefreshToken()))).thenReturn(true);
        doNothing().when(distributedLock).unlock(anyString());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            tokenService.refreshToken(originalPair.getRefreshToken());
        });

        assertEquals("Refresh Token 已使用，请重新登录", exception.getMessage());
    }

    @Test
    void revokeToken_ShouldRevokeValidToken() {
        String userId = "user-123";
        String username = "testuser";

        doNothing().when(tokenStore).saveAccessToken(anyString(), anyString(), anyLong());
        doNothing().when(tokenStore).saveRefreshToken(anyString(), anyString(), anyLong());

        TokenPair tokenPair = tokenService.generateTokenPair(userId, username);

        doNothing().when(tokenStore).addToBlacklist(anyString(), anyLong());
        doNothing().when(tokenStore).removeAccessToken(anyString());

        boolean result = tokenService.revokeToken(tokenPair.getAccessToken());

        assertTrue(result);
        verify(tokenStore).addToBlacklist(eq(tokenPair.getAccessToken()), anyLong());
        verify(tokenStore).removeAccessToken(eq(userId));
    }

    @Test
    void validateToken_ShouldReturnInvalid_WhenTokenIsNull() {
        ValidationResult result = tokenService.validateToken(null);

        assertFalse(result.isValid());
        assertEquals("Token 为空或无效", result.getMessage());
    }

    @Test
    void validateToken_ShouldReturnInvalid_WhenTokenIsEmpty() {
        ValidationResult result = tokenService.validateToken("   ");

        assertFalse(result.isValid());
        assertEquals("Token 为空或无效", result.getMessage());
    }

    @Test
    void refreshToken_ShouldThrowException_WhenTokenIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            tokenService.refreshToken(null);
        });

        assertEquals("Refresh Token 不能为空", exception.getMessage());
    }

    @Test
    void revokeToken_ShouldReturnFalse_WhenTokenIsNull() {
        boolean result = tokenService.revokeToken(null);

        assertFalse(result);
    }
}
