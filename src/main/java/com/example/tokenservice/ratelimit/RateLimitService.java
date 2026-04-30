package com.example.tokenservice.ratelimit;

import com.example.tokenservice.config.TokenProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final TokenProperties tokenProperties;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> userRateLimitMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> ipRateLimitMap = new ConcurrentHashMap<>();

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
            userAllowed = checkAndRecord(userRateLimitMap, userId, 
                    tokenProperties.getRateLimit().getRequestsPerMinutePerUser());
        }

        if (clientIp != null && !clientIp.isEmpty()) {
            ipAllowed = checkAndRecord(ipRateLimitMap, clientIp, 
                    tokenProperties.getRateLimit().getRequestsPerMinutePerIp());
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

        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60000;

        boolean userAllowed = true;
        boolean ipAllowed = true;

        if (userId != null && !userId.isEmpty()) {
            userAllowed = checkBatch(userRateLimitMap, userId, oneMinuteAgo, 
                    tokenProperties.getRateLimit().getRequestsPerMinutePerUser(), count);
        }

        if (clientIp != null && !clientIp.isEmpty()) {
            ipAllowed = checkBatch(ipRateLimitMap, clientIp, oneMinuteAgo, 
                    tokenProperties.getRateLimit().getRequestsPerMinutePerIp(), count);
        }

        if (userAllowed && ipAllowed) {
            for (int i = 0; i < count; i++) {
                if (userId != null && !userId.isEmpty()) {
                    record(userRateLimitMap, userId, now);
                }
                if (clientIp != null && !clientIp.isEmpty()) {
                    record(ipRateLimitMap, clientIp, now);
                }
            }
        }

        return userAllowed && ipAllowed;
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
            CopyOnWriteArrayList<Long> timestamps = userRateLimitMap.get(userId);
            int count = countRecentRequests(timestamps, oneMinuteAgo);
            int limit = tokenProperties.getRateLimit().getRequestsPerMinutePerUser();
            info.setUserRemaining(limit - count);
            info.setUserLimit(limit);
        }

        if (clientIp != null && !clientIp.isEmpty()) {
            CopyOnWriteArrayList<Long> timestamps = ipRateLimitMap.get(clientIp);
            int count = countRecentRequests(timestamps, oneMinuteAgo);
            int limit = tokenProperties.getRateLimit().getRequestsPerMinutePerIp();
            info.setIpRemaining(limit - count);
            info.setIpLimit(limit);
        }

        info.setMaxBatchSize(tokenProperties.getRateLimit().getMaxBatchSize());

        return info;
    }

    public void resetUserRateLimit(String userId) {
        if (userId != null && !userId.isEmpty()) {
            userRateLimitMap.remove(userId);
            log.info("已重置用户限流: userId={}", userId);
        }
    }

    public void resetIpRateLimit(String clientIp) {
        if (clientIp != null && !clientIp.isEmpty()) {
            ipRateLimitMap.remove(clientIp);
            log.info("已重置IP限流: ip={}", clientIp);
        }
    }

    public void resetAllRateLimits() {
        userRateLimitMap.clear();
        ipRateLimitMap.clear();
        log.info("已重置所有限流");
    }

    private boolean checkAndRecord(ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> map, 
                                    String key, int limit) {
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60000;

        CopyOnWriteArrayList<Long> timestamps = map.get(key);
        if (timestamps == null) {
            timestamps = new CopyOnWriteArrayList<>();
            CopyOnWriteArrayList<Long> existing = map.putIfAbsent(key, timestamps);
            if (existing != null) {
                timestamps = existing;
            }
        }

        int count = countRecentRequests(timestamps, oneMinuteAgo);
        
        if (count >= limit) {
            return false;
        }

        timestamps.add(now);
        return true;
    }

    private boolean checkBatch(ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> map,
                               String key, long oneMinuteAgo, int limit, int count) {
        CopyOnWriteArrayList<Long> timestamps = map.get(key);
        if (timestamps == null) {
            return count <= limit;
        }

        int currentCount = countRecentRequests(timestamps, oneMinuteAgo);
        return (currentCount + count) <= limit;
    }

    private void record(ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> map, 
                        String key, long timestamp) {
        CopyOnWriteArrayList<Long> timestamps = map.get(key);
        if (timestamps == null) {
            timestamps = new CopyOnWriteArrayList<>();
            CopyOnWriteArrayList<Long> existing = map.putIfAbsent(key, timestamps);
            if (existing != null) {
                timestamps = existing;
            }
        }
        timestamps.add(timestamp);
    }

    private int countRecentRequests(CopyOnWriteArrayList<Long> timestamps, long oneMinuteAgo) {
        if (timestamps == null || timestamps.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Long timestamp : timestamps) {
            if (timestamp != null && timestamp > oneMinuteAgo) {
                count++;
            }
        }
        return count;
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

    private void cleanupExpiredEntries(ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> map, 
                                         long threshold) {
        Iterator<Map.Entry<String, CopyOnWriteArrayList<Long>>> iterator = map.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, CopyOnWriteArrayList<Long>> entry = iterator.next();
            CopyOnWriteArrayList<Long> timestamps = entry.getValue();
            
            Iterator<Long> tsIterator = timestamps.iterator();
            while (tsIterator.hasNext()) {
                Long timestamp = tsIterator.next();
                if (timestamp == null || timestamp <= threshold) {
                    tsIterator.remove();
                }
            }
            
            if (timestamps.isEmpty()) {
                iterator.remove();
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
}
