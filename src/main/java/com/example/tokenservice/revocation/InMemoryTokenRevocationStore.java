package com.example.tokenservice.revocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@ConditionalOnProperty(name = "token.revocation.storage-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryTokenRevocationStore implements TokenRevocationStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTokenRevocationStore.class);

    private final ConcurrentHashMap<String, RevocationEntry> jwtIdBlacklist = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RevocationEntry> tokenValueBlacklist = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void revokeByJwtId(String jwtId, String reason) {
        if (jwtId == null || jwtId.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            RevocationEntry entry = new RevocationEntry(jwtId, reason, LocalDateTime.now());
            jwtIdBlacklist.put(jwtId, entry);
            log.info("Token 已加入黑名单（jwtId）: {}, 原因: {}", jwtId, reason);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void revokeByTokenValue(String tokenValue, String reason) {
        if (tokenValue == null || tokenValue.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            RevocationEntry entry = new RevocationEntry(tokenValue, reason, LocalDateTime.now());
            tokenValueBlacklist.put(tokenValue, entry);
            log.info("Token 已加入黑名单（tokenValue）, 原因: {}", reason);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean isRevoked(String jwtId, String tokenValue) {
        lock.readLock().lock();
        try {
            if (jwtId != null && !jwtId.isEmpty()) {
                RevocationEntry entry = jwtIdBlacklist.get(jwtId);
                if (entry != null) {
                    log.debug("Token 在黑名单中（jwtId）: {}", jwtId);
                    return true;
                }
            }
            if (tokenValue != null && !tokenValue.isEmpty()) {
                RevocationEntry entry = tokenValueBlacklist.get(tokenValue);
                if (entry != null) {
                    log.debug("Token 在黑名单中（tokenValue）");
                    return true;
                }
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void removeFromBlacklist(String jwtId) {
        if (jwtId == null || jwtId.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            jwtIdBlacklist.remove(jwtId);
            log.info("Token 已从黑名单移除: {}", jwtId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clearExpiredEntries(long maxAgeSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(maxAgeSeconds);
        lock.writeLock().lock();
        try {
            int removedCount = 0;
            Iterator<Map.Entry<String, RevocationEntry>> jwtIdIterator = jwtIdBlacklist.entrySet().iterator();
            while (jwtIdIterator.hasNext()) {
                Map.Entry<String, RevocationEntry> entry = jwtIdIterator.next();
                if (entry.getValue().getRevokedAt().isBefore(threshold)) {
                    jwtIdIterator.remove();
                    removedCount++;
                }
            }
            Iterator<Map.Entry<String, RevocationEntry>> tokenValueIterator = tokenValueBlacklist.entrySet().iterator();
            while (tokenValueIterator.hasNext()) {
                Map.Entry<String, RevocationEntry> entry = tokenValueIterator.next();
                if (entry.getValue().getRevokedAt().isBefore(threshold)) {
                    tokenValueIterator.remove();
                    removedCount++;
                }
            }
            if (removedCount > 0) {
                log.info("清理过期黑名单条目: {} 条", removedCount);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long getBlacklistSize() {
        lock.readLock().lock();
        try {
            return jwtIdBlacklist.size() + tokenValueBlacklist.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
