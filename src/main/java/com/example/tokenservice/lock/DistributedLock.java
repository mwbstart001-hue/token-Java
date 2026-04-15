package com.example.tokenservice.lock;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public interface DistributedLock {
    
    boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit);
    
    void unlock(String key);
    
    <T> T executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> supplier);
    
    void executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Runnable runnable);
}
