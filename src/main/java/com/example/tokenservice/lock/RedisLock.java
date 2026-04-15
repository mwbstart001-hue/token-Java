package com.example.tokenservice.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@ConditionalOnProperty(name = "token.storage.type", havingValue = "redis")
public class RedisLock implements DistributedLock {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ThreadLocal<String> lockValue = new ThreadLocal<>();
    
    private static final String LOCK_PREFIX = "lock:";
    private static final String UNLOCK_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
    
    private final DefaultRedisScript<Long> unlockScript;
    
    public RedisLock(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = new DefaultRedisScript<>();
        this.unlockScript.setScriptText(UNLOCK_SCRIPT);
        this.unlockScript.setResultType(Long.class);
    }
    
    @Override
    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
        String lockKey = LOCK_PREFIX + key;
        String value = UUID.randomUUID().toString();
        
        try {
            long startTime = System.currentTimeMillis();
            long waitMillis = unit.toMillis(waitTime);
            
            while (System.currentTimeMillis() - startTime < waitMillis) {
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, value, leaseTime, unit);
                
                if (Boolean.TRUE.equals(acquired)) {
                    lockValue.set(value);
                    log.debug("Acquired Redis lock for key: {}, value: {}", lockKey, value);
                    return true;
                }
                
                Thread.sleep(50);
            }
            
            log.debug("Failed to acquire Redis lock for key: {} within wait time", lockKey);
            return false;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring Redis lock for key: {}", key, e);
            return false;
        } catch (Exception e) {
            log.error("Error acquiring Redis lock for key: {}", key, e);
            return false;
        }
    }
    
    @Override
    public void unlock(String key) {
        String lockKey = LOCK_PREFIX + key;
        String value = lockValue.get();
        
        if (value == null) {
            log.warn("No lock value found for key: {}", lockKey);
            return;
        }
        
        try {
            Long result = redisTemplate.execute(
                    unlockScript,
                    Collections.singletonList(lockKey),
                    value
            );
            log.debug("Released Redis lock for key: {}, result: {}", lockKey, result);
        } catch (Exception e) {
            log.error("Error releasing Redis lock for key: {}", lockKey, e);
        } finally {
            lockValue.remove();
        }
    }
    
    @Override
    public <T> T executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> supplier) {
        boolean locked = tryLock(key, waitTime, leaseTime, unit);
        if (!locked) {
            throw new IllegalStateException("Failed to acquire Redis lock for key: " + key);
        }
        try {
            return supplier.get();
        } finally {
            unlock(key);
        }
    }
    
    @Override
    public void executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Runnable runnable) {
        boolean locked = tryLock(key, waitTime, leaseTime, unit);
        if (!locked) {
            throw new IllegalStateException("Failed to acquire Redis lock for key: " + key);
        }
        try {
            runnable.run();
        } finally {
            unlock(key);
        }
    }
}
