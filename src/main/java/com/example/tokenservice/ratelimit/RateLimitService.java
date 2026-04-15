package com.example.tokenservice.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimitService {
    
    private final Map<String, RateLimitWindow> windows = new ConcurrentHashMap<>();
    
    private static final int DEFAULT_LIMIT = 10;
    private static final long DEFAULT_WINDOW_MS = 60000;
    
    public boolean tryAcquire(String key) {
        return tryAcquire(key, DEFAULT_LIMIT, DEFAULT_WINDOW_MS);
    }
    
    public boolean tryAcquire(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        
        RateLimitWindow window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startTime > windowMs) {
                return new RateLimitWindow(now, new AtomicInteger(0));
            }
            return existing;
        });
        
        int current = window.counter.incrementAndGet();
        
        if (current > limit) {
            log.warn("Rate limit exceeded for key: {}, current: {}, limit: {}", key, current, limit);
            window.counter.decrementAndGet();
            return false;
        }
        
        log.debug("Rate limit check passed for key: {}, current: {}, limit: {}", key, current, limit);
        return true;
    }
    
    public void reset(String key) {
        windows.remove(key);
    }
    
    private static class RateLimitWindow {
        final long startTime;
        final AtomicInteger counter;
        
        RateLimitWindow(long startTime, AtomicInteger counter) {
            this.startTime = startTime;
            this.counter = counter;
        }
    }
}
