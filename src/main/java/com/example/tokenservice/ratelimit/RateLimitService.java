package com.example.tokenservice.ratelimit;

import com.example.tokenservice.config.TokenProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final TokenProperties tokenProperties;

    private final ConcurrentHashMap<String, RateLimitTracker> userRateLimitMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimitTracker> ipRateLimitMap = new ConcurrentHashMap<>();

    private final AtomicInteger cleanupCounter = new AtomicInteger(0);
    private static final int CLEANUP_INTERVAL = 100;

    public RateLimitService(TokenProperties tokenProperties) {
        this.tokenProperties = tokenProperties;
        log.info("Token 限流服务已初始化");
    }

    public boolean tryAcquire(String userId, String clientIp) {
        if (!tokenProperties.getRateLimit().isEnabled()) {
            return true;
        }

        cleanupIfNeeded();

        boolean userAllowed = true;
        boolean ipAllowed = true;

        if (userId != null && !userId.isEmpty()) {
            userAllowed = tryAcquireInternal(
                    userRateLimitMap, 
                    userId, 
                    tokenProperties.getRateLimit().getRequestsPerMinutePerUser(),
                    1
            );
        }

        if (clientIp != null && !clientIp.isEmpty()) {
            ipAllowed = tryAcquireInternal(
                    ipRateLimitMap, 
                    clientIp, 
                    tokenProperties.getRateLimit().getRequestsPerMinutePerIp(),
                    1
            );
        }

        if (!userAllowed) {
            log.warn("用户触发限流: userId={}, 限制={}/分钟", userId, 
                    tokenProperties.getRateLimit().getRequestsPerMinutePerUser());
        }
        if (!ipAllowed) {
            log.warn("IP触发限流: ip={}, 限制={}/分钟", clientIp, 
                    tokenProperties.getRateLimit().getRequestsPerMinutePerIp());
        }

        return userAllowed && ipAllowed;
    }

    public boolean tryAcquireBatch(String userId, String clientIp, int count) {
        if (!tokenProperties.getRateLimit().isEnabled()) {
            return true;
        }

        int maxBatchSize = tokenProperties.getRateLimit().getMaxBatchSize();
        if (count > maxBatchSize) {
            log.warn("批量请求数量超过限制: count={}, max={}", count, maxBatchSize);
            return false;
        }

        cleanupIfNeeded();

        boolean userAllowed = true;
        boolean ipAllowed = true;

        if (userId != null && !userId.isEmpty()) {
            userAllowed = tryAcquireInternal(
                    userRateLimitMap, 
                    userId, 
                    tokenProperties.getRateLimit().getRequestsPerMinutePerUser(),
                    count
            );
        }

        if (clientIp != null && !clientIp.isEmpty()) {
            ipAllowed = tryAcquireInternal(
                    ipRateLimitMap, 
                    clientIp, 
                    tokenProperties.getRateLimit().getRequestsPerMinutePerIp(),
                    count
            );
        }

        return userAllowed && ipAllowed;
    }

    private boolean tryAcquireInternal(ConcurrentHashMap<String, RateLimitTracker> map,
                                        String key, int limit, int count) {
        RateLimitTracker tracker = map.computeIfAbsent(key, k -> new RateLimitTracker());
        return tracker.tryAcquire(limit, count);
    }

    public RateLimitInfo getRateLimitInfo(String userId, String clientIp) {
        RateLimitInfo info = new RateLimitInfo();
        info.setEnabled(tokenProperties.getRateLimit().isEnabled());
        
        if (!info.isEnabled()) {
            return info;
        }

        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60000;

        if (userId != null && !userId.isEmpty()) {
            RateLimitTracker tracker = userRateLimitMap.get(userId);
            int count = tracker != null ? tracker.getCountSince(oneMinuteAgo) : 0;
            int limit = tokenProperties.getRateLimit().getRequestsPerMinutePerUser();
            info.setUserRemaining(limit - count);
            info.setUserLimit(limit);
        }

        if (clientIp != null && !clientIp.isEmpty()) {
            RateLimitTracker tracker = ipRateLimitMap.get(clientIp);
            int count = tracker != null ? tracker.getCountSince(oneMinuteAgo) : 0;
            int limit = tokenProperties.getRateLimit().getRequestsPerMinutePerIp();
            info.setIpRemaining(limit - count);
            info.setIpLimit(limit);
        }

        info.setMaxBatchSize(tokenProperties.getRateLimit().getMaxBatchSize());

        return info;
    }

    public void resetUserRateLimit(String userId) {
        if (userId != null && !userId.isEmpty()) {
            RateLimitTracker tracker = userRateLimitMap.remove(userId);
            if (tracker != null) {
                tracker.clear();
            }
            log.info("已重置用户限流: userId={}", userId);
        }
    }

    public void resetIpRateLimit(String clientIp) {
        if (clientIp != null && !clientIp.isEmpty()) {
            RateLimitTracker tracker = ipRateLimitMap.remove(clientIp);
            if (tracker != null) {
                tracker.clear();
            }
            log.info("已重置IP限流: ip={}", clientIp);
        }
    }

    public void resetAllRateLimits() {
        userRateLimitMap.clear();
        ipRateLimitMap.clear();
        log.info("已重置所有限流");
    }

    private void cleanupIfNeeded() {
        int count = cleanupCounter.incrementAndGet();
        if (count % CLEANUP_INTERVAL != 0) {
            return;
        }

        long oneMinuteAgo = System.currentTimeMillis() - 60000;
        
        cleanupExpiredEntries(userRateLimitMap, oneMinuteAgo);
        cleanupExpiredEntries(ipRateLimitMap, oneMinuteAgo);
        
        log.debug("限流清理完成");
    }

    private void cleanupExpiredEntries(ConcurrentHashMap<String, RateLimitTracker> map, 
                                         long threshold) {
        Iterator<Map.Entry<String, RateLimitTracker>> iterator = map.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, RateLimitTracker> entry = iterator.next();
            RateLimitTracker tracker = entry.getValue();
            
            int count = tracker.getCountSince(threshold);
            
            if (count == 0) {
                iterator.remove();
            } else {
                tracker.removeBefore(threshold);
            }
        }
    }

    public static class RateLimitInfo {
        private boolean enabled;
        private int userLimit;
        private int userRemaining;
        private int ipLimit;
        private int ipRemaining;
        private int maxBatchSize;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getUserLimit() {
            return userLimit;
        }

        public void setUserLimit(int userLimit) {
            this.userLimit = userLimit;
        }

        public int getUserRemaining() {
            return userRemaining;
        }

        public void setUserRemaining(int userRemaining) {
            this.userRemaining = userRemaining;
        }

        public int getIpLimit() {
            return ipLimit;
        }

        public void setIpLimit(int ipLimit) {
            this.ipLimit = ipLimit;
        }

        public int getIpRemaining() {
            return ipRemaining;
        }

        public void setIpRemaining(int ipRemaining) {
            this.ipRemaining = ipRemaining;
        }

        public int getMaxBatchSize() {
            return maxBatchSize;
        }

        public void setMaxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
        }
    }

    private static class RateLimitTracker {
        private final StampedLock lock = new StampedLock();
        private final List<Long> timestamps = new ArrayList<>();

        public boolean tryAcquire(int limit, int count) {
            long stamp = lock.writeLock();
            try {
                long now = System.currentTimeMillis();
                long oneMinuteAgo = now - 60000;

                removeExpiredTimestamps(oneMinuteAgo);

                if (timestamps.size() + count > limit) {
                    return false;
                }

                for (int i = 0; i < count; i++) {
                    timestamps.add(now);
                }

                return true;
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        public int getCountSince(long threshold) {
            long stamp = lock.tryOptimisticRead();
            int count = 0;
            for (Long ts : timestamps) {
                if (ts > threshold) {
                    count++;
                }
            }
            if (lock.validate(stamp)) {
                return count;
            }
            
            stamp = lock.readLock();
            try {
                count = 0;
                for (Long ts : timestamps) {
                    if (ts > threshold) {
                        count++;
                    }
                }
                return count;
            } finally {
                lock.unlockRead(stamp);
            }
        }

        public void removeBefore(long threshold) {
            long stamp = lock.writeLock();
            try {
                removeExpiredTimestamps(threshold);
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        public void clear() {
            long stamp = lock.writeLock();
            try {
                timestamps.clear();
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        private void removeExpiredTimestamps(long threshold) {
            Iterator<Long> iterator = timestamps.iterator();
            while (iterator.hasNext()) {
                Long ts = iterator.next();
                if (ts <= threshold) {
                    iterator.remove();
                } else {
                    break;
                }
            }
        }
    }
}
