package com.example.tokenservice.service;

import com.example.tokenservice.config.JwtKeyManager;
import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.dispatcher.CommandHook;
import com.example.tokenservice.dispatcher.TokenOperationDispatcher;
import com.example.tokenservice.factory.TokenOperationFactory;
import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import com.example.tokenservice.strategy.JwtTokenGenerator;
import com.example.tokenservice.strategy.JwtTokenValidator;
import com.example.tokenservice.strategy.TokenGenerator;
import com.example.tokenservice.strategy.TokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TokenService 单元测试
 * 
 * 重构说明：
 * 1. 使用设计模式重构后，TokenService 现在委托给调度器
 * 2. 测试仍然使用真实的策略（JwtTokenGenerator/JwtTokenValidator）
 * 3. 只 Mock TokenStore（数据存储层）
 * 4. 架构：TokenService → Dispatcher → Factory → Command → Strategy
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenServiceTest {

    @Mock
    private TokenStore tokenStore;

    private TokenProperties tokenProperties;
    private JwtKeyManager jwtKeyManager;
    private TokenGenerator tokenGenerator;
    private TokenValidator tokenValidator;
    private TokenOperationFactory operationFactory;
    private TokenOperationDispatcher dispatcher;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenProperties = new TokenProperties();
        tokenProperties.setSecret("test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm");
        tokenProperties.setDefaultExpireSeconds(3600L);
        tokenProperties.setMaxExpireSeconds(86400L * 30);
        tokenProperties.setAlgorithm("RS256");

        jwtKeyManager = new JwtKeyManager(tokenProperties);
        jwtKeyManager.init();

        tokenGenerator = new JwtTokenGenerator(jwtKeyManager);
        tokenValidator = new JwtTokenValidator(jwtKeyManager);

        operationFactory = new TokenOperationFactory(
            tokenProperties, tokenStore, tokenGenerator, tokenValidator
        );

        dispatcher = new TokenOperationDispatcher(operationFactory, tokenProperties, new ArrayList<>());

        tokenService = new TokenService(dispatcher, tokenStore, jwtKeyManager);
    }

    @Test
    void generateToken_ShouldReturnValidToken() {
        when(tokenStore.save(any(Token.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String token = tokenService.generateToken("user123", "test-subject", null);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        verify(tokenStore, times(1)).save(any(Token.class));
    }

    @Test
    void validateToken_WithValidToken_ShouldReturnTrue() {
        String tokenValue = generateTestToken("user123", 3600);
        Token token = createToken(tokenValue, "user123", TokenStatus.ACTIVE, LocalDateTime.now().plusHours(1));

        when(tokenStore.findByTokenValue(tokenValue)).thenReturn(Optional.of(token));

        boolean result = tokenService.validateToken(tokenValue);

        assertTrue(result);
    }

    @Test
    void validateToken_WithInvalidatedToken_ShouldReturnFalse() {
        String tokenValue = generateTestToken("user123", 3600);
        Token token = createToken(tokenValue, "user123", TokenStatus.INVALIDATED, LocalDateTime.now().plusHours(1));

        when(tokenStore.findByTokenValue(tokenValue)).thenReturn(Optional.of(token));

        boolean result = tokenService.validateToken(tokenValue);

        assertFalse(result);
    }

    @Test
    void validateToken_WithExpiredToken_ShouldReturnFalse() {
        String tokenValue = generateTestToken("user123", 1);
        Token token = createToken(tokenValue, "user123", TokenStatus.ACTIVE, LocalDateTime.now().minusSeconds(1));

        when(tokenStore.findByTokenValue(tokenValue)).thenReturn(Optional.of(token));

        boolean result = tokenService.validateToken(tokenValue);

        assertFalse(result);
    }

    @Test
    void validateToken_WithNonExistentToken_ShouldReturnFalse() {
        when(tokenStore.findByTokenValue(anyString())).thenReturn(Optional.empty());

        boolean result = tokenService.validateToken("non-existent-token");

        assertFalse(result);
    }

    @Test
    void invalidateToken_WithActiveToken_ShouldReturnTrue() {
        String tokenValue = generateTestToken("user123", 3600);
        Token token = createToken(tokenValue, "user123", TokenStatus.ACTIVE, LocalDateTime.now().plusHours(1));

        when(tokenStore.findByTokenValue(tokenValue)).thenReturn(Optional.of(token));
        when(tokenStore.updateStatusIfActive(eq(tokenValue), eq(TokenStatus.INVALIDATED))).thenReturn(true);

        boolean result = tokenService.invalidateToken(tokenValue);

        assertTrue(result);
        verify(tokenStore, times(1)).updateStatusIfActive(eq(tokenValue), eq(TokenStatus.INVALIDATED));
    }

    @Test
    void invalidateToken_WithAlreadyInvalidatedToken_ShouldReturnFalse() {
        String tokenValue = generateTestToken("user123", 3600);
        Token token = createToken(tokenValue, "user123", TokenStatus.INVALIDATED, LocalDateTime.now().plusHours(1));

        when(tokenStore.findByTokenValue(tokenValue)).thenReturn(Optional.of(token));

        boolean result = tokenService.invalidateToken(tokenValue);

        assertFalse(result);
        verify(tokenStore, never()).updateStatusIfActive(anyString(), any());
        verify(tokenStore, never()).updateStatus(anyString(), any());
    }

    @Test
    void generateToken_ConcurrentAccess_ShouldBeThreadSafe() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        when(tokenStore.save(any(Token.class))).thenAnswer(invocation -> {
            successCount.incrementAndGet();
            return invocation.getArgument(0);
        });

        for (int i = 0; i < threadCount; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    tokenService.generateToken("user-" + userId, "subject-" + userId, 3600L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount, successCount.get());
    }

    private String generateTestToken(String userId, long expireSeconds) {
        when(tokenStore.save(any(Token.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return tokenService.generateToken(userId, "test", expireSeconds);
    }

    private Token createToken(String tokenValue, String userId, TokenStatus status, LocalDateTime expiresAt) {
        Token token = new Token();
        token.setTokenValue(tokenValue);
        token.setUserId(userId);
        token.setStatus(status);
        token.setIssuedAt(LocalDateTime.now());
        token.setExpiresAt(expiresAt);
        return token;
    }
}
