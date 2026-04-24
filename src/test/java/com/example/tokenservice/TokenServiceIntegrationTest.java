package com.example.tokenservice;

import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import com.example.tokenservice.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TokenServiceIntegrationTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private TokenStore tokenStore;

    @Autowired
    private TokenProperties tokenProperties;

    @Test
    void fullTokenLifecycle_ShouldWorkCorrectly() {
        String token = tokenService.generateToken("integration-user", "test-subject", 3600L);
        assertNotNull(token);

        boolean valid = tokenService.validateToken(token);
        assertTrue(valid);

        Optional<Token> storedToken = tokenStore.findByTokenValue(token);
        assertTrue(storedToken.isPresent());
        assertEquals("integration-user", storedToken.get().getUserId());
        assertEquals(TokenStatus.ACTIVE, storedToken.get().getStatus());

        boolean invalidated = tokenService.invalidateToken(token);
        assertTrue(invalidated);

        boolean stillValid = tokenService.validateToken(token);
        assertFalse(stillValid);

        Optional<Token> invalidatedToken = tokenStore.findByTokenValue(token);
        assertTrue(invalidatedToken.isPresent());
        assertEquals(TokenStatus.INVALIDATED, invalidatedToken.get().getStatus());
    }

    @Test
    void generateToken_WithCustomExpiration_ShouldRespectExpiration() {
        String token = tokenService.generateToken("expire-user", "test", 1L);
        assertNotNull(token);

        boolean valid = tokenService.validateToken(token);
        assertTrue(valid);

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean stillValid = tokenService.validateToken(token);
        assertFalse(stillValid);

        Optional<Token> expiredToken = tokenStore.findByTokenValue(token);
        assertTrue(expiredToken.isPresent());
        assertEquals(TokenStatus.EXPIRED, expiredToken.get().getStatus());
    }

    @Test
    void concurrentTokenGeneration_ShouldBeThreadSafe() throws InterruptedException {
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String token = tokenService.generateToken("concurrent-user-" + index, "test", 3600L);
                    if (token != null && !token.isEmpty()) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount, successCount.get());
    }

    @Test
    void concurrentTokenValidation_ShouldBeThreadSafe() throws InterruptedException {
        String token = tokenService.generateToken("validate-user", "test", 3600L);
        assertNotNull(token);

        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger validCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean valid = tokenService.validateToken(token);
                    if (valid) {
                        validCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount, validCount.get());
    }

    @Test
    void concurrentTokenInvalidation_ShouldBeThreadSafe() throws InterruptedException {
        String token = tokenService.generateToken("invalidate-user", "test", 3600L);
        assertNotNull(token);

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean success = tokenService.invalidateToken(token);
                    if (success) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(1, successCount.get());
    }
}
