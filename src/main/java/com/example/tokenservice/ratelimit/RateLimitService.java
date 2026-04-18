package com.example.tokenservice.ratelimit;

import com.example.tokenservice.common.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimitService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private DefaultRedisScript<Long> slidingWindowScript;
    
    private final Map<String, Map<String, SlidingWindow>> memoryWindows = new ConcurrentHashMap<>();
    
    private static final int DEFAULT_LIMIT = 10;
    private static final long DEFAULT_WINDOW_MS = 60000;
    private static final String KEY_PREFIX = "ratelimit:";
    
    public RateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @PostConstruct
    public void init() {
        slidingWindowScript = new DefaultRedisScript<>();
        slidingWindowScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("ratelimit/sliding_window.lua")));
        slidingWindowScript.setResultType(Long.class);
    }
    
    private String getRateLimitKey(String key) {
        String tenantId = TenantContext.getTenantId();
        return KEY_PREFIX + tenantId + ":" + key;
    }
    
    public boolean tryAcquire(String key) {
        return tryAcquire(key, DEFAULT_LIMIT, DEFAULT_WINDOW_MS);
    }
    
    public boolean tryAcquire(String key, int limit, long windowMs) {
        String redisKey = getRateLimitKey(key);
        long currentTime = System.currentTimeMillis();
        
        try {
            Long result = redisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(redisKey),
                    limit,
                    windowMs,
                    currentTime
            );
            
            boolean allowed = result != null && result == 1;
            
            if (!allowed) {
                log.warn("Rate limit exceeded for key: {}, tenant: {}, limit: {}, window: {}ms", 
                        key, TenantContext.getTenantId(), limit, windowMs);
            } else {
                log.debug("Rate limit check passed for key: {}, tenant: {}, limit: {}, window: {}ms", 
                        key, TenantContext.getTenantId(), limit, windowMs);
            }
            
            return allowed;
            
        } catch (Exception e) {
            log.warn("Redis rate limit failed, falling back to memory rate limit. key: {}, error: {}", 
                    key, e.getMessage());
            return tryAcquireMemory(key, limit, windowMs);
        }
    }
    
    private boolean tryAcquireMemory(String key, int limit, long windowMs) {
        String tenantId = TenantContext.getTenantId();
        Map<String, SlidingWindow> tenantWindows = memoryWindows.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        
        SlidingWindow window = tenantWindows.compute(key, (k, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null) {
                return new SlidingWindow(windowMs);
            }
            existing.cleanOldRequests(now);
            return existing;
        });
        
        boolean allowed = window.tryAcquire(limit);
        
        if (!allowed) {
            log.warn("Memory rate limit exceeded for key: {}, tenant: {}, limit: {}", 
                    key, tenantId, limit);
        }
        
        return allowed;
    }
    
    public void reset(String key) {
        String redisKey = getRateLimitKey(key);
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Failed to reset redis rate limit key: {}, error: {}", redisKey, e.getMessage());
        }
        
        String tenantId = TenantContext.getTenantId();
        Map<String, SlidingWindow> tenantWindows = memoryWindows.get(tenantId);
        if (tenantWindows != null) {
            tenantWindows.remove(key);
        }
    }
    
    private static class SlidingWindow {
        private final long windowMs;
        private final Map<Long, AtomicInteger> requests = new ConcurrentHashMap<>();
        
        SlidingWindow(long windowMs) {
            this.windowMs = windowMs;
        }
        
        void cleanOldRequests(long now) {
            long windowStart = now - windowMs;
            requests.keySet().removeIf(timestamp -> timestamp < windowStart);
        }
        
        boolean tryAcquire(int limit) {
            long now = System.currentTimeMillis();
            long windowStart = now - windowMs;
            
            int total = 0;
            for (Map.Entry<Long, AtomicInteger> entry : requests.entrySet()) {
                if (entry.getKey() >= windowStart) {
                    total += entry.getValue().get();
                }
            }
            
            if (total >= limit) {
                return false;
            }
            
            long currentBucket = now / 1000 * 1000;
            requests.computeIfAbsent(currentBucket, k -> new AtomicInteger(0)).incrementAndGet();
            return true;
        }
    }
}
