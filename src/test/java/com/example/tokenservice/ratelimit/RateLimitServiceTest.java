package com.example.tokenservice.ratelimit;

import com.example.tokenservice.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void tryAcquire_ShouldUseMemoryFallback_WhenRedisFails() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        String key = "test-key";
        int limit = 5;
        long windowMs = 60000;

        for (int i = 0; i < limit; i++) {
            boolean result = rateLimitService.tryAcquire(key, limit, windowMs);
            assertTrue(result, "Request " + (i + 1) + " should be allowed");
        }

        boolean result = rateLimitService.tryAcquire(key, limit, windowMs);
        assertFalse(result, "Request " + (limit + 1) + " should be rate limited");
    }

    @Test
    void tryAcquire_ShouldBeIsolatedBetweenTenants() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        String key = "shared-key";
        int limit = 2;
        long windowMs = 60000;

        TenantContext.setTenantId("tenant-1");
        assertTrue(rateLimitService.tryAcquire(key, limit, windowMs));
        assertTrue(rateLimitService.tryAcquire(key, limit, windowMs));

        TenantContext.setTenantId("tenant-2");
        assertTrue(rateLimitService.tryAcquire(key, limit, windowMs));
        assertTrue(rateLimitService.tryAcquire(key, limit, windowMs));

        TenantContext.setTenantId("tenant-1");
        assertFalse(rateLimitService.tryAcquire(key, limit, windowMs));
    }

    @Test
    void tryAcquire_WithDefaultParams_ShouldWork() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        String key = "test-key";

        boolean result = rateLimitService.tryAcquire(key);

        assertTrue(result);
    }

    @Test
    void reset_ShouldResetRateLimit() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        String key = "test-key";
        int limit = 2;
        long windowMs = 60000;

        TenantContext.setTenantId("tenant-1");
        
        assertTrue(rateLimitService.tryAcquire(key, limit, windowMs));
        assertTrue(rateLimitService.tryAcquire(key, limit, windowMs));
        assertFalse(rateLimitService.tryAcquire(key, limit, windowMs));

        rateLimitService.reset(key);

        assertTrue(rateLimitService.tryAcquire(key, limit, windowMs));
    }

    @Test
    void tryAcquire_ShouldUseDefaultTenant_WhenNotSet() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        String key = "test-key";
        int limit = 1;
        long windowMs = 60000;

        assertTrue(rateLimitService.tryAcquire(key, limit, windowMs));
        
        TenantContext.setTenantId("default");
        assertFalse(rateLimitService.tryAcquire(key, limit, windowMs));
    }
}
