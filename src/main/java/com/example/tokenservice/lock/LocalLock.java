package com.example.tokenservice.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Slf4j
@Component
@ConditionalOnProperty(name = "token.storage.type", havingValue = "memory", matchIfMissing = true)
public class LocalLock implements DistributedLock {
    
    private final Map<String, Lock> lockMap = new ConcurrentHashMap<>();
    
    @Override
    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
        Lock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock());
        try {
            boolean acquired = lock.tryLock(waitTime, unit);
            if (acquired) {
                log.debug("Acquired local lock for key: {}", key);
            } else {
                log.debug("Failed to acquire local lock for key: {}", key);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring local lock for key: {}", key, e);
            return false;
        }
    }
    
    @Override
    public void unlock(String key) {
        Lock lock = lockMap.get(key);
        if (lock != null) {
            lock.unlock();
            log.debug("Released local lock for key: {}", key);
        }
    }
    
    @Override
    public <T> T executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> supplier) {
        boolean locked = tryLock(key, waitTime, leaseTime, unit);
        if (!locked) {
            throw new IllegalStateException("Failed to acquire lock for key: " + key);
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
            throw new IllegalStateException("Failed to acquire lock for key: " + key);
        }
        try {
            runnable.run();
        } finally {
            unlock(key);
        }
    }
}
