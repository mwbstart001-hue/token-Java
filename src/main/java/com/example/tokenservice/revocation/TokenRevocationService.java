package com.example.tokenservice.revocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final TokenRevocationStore revocationStore;

    public TokenRevocationService(TokenRevocationStore revocationStore) {
        this.revocationStore = revocationStore;
        log.info("Token 吊销黑名单服务已初始化");
    }

    public void revokeByJwtId(String jwtId, String reason) {
        if (jwtId == null || jwtId.isEmpty()) {
            log.warn("尝试吊销空 jwtId");
            return;
        }
        revocationStore.revokeByJwtId(jwtId, reason != null ? reason : "手动吊销");
        log.info("Token 已吊销（jwtId）: {}", jwtId);
    }

    public void revokeByTokenValue(String tokenValue, String reason) {
        if (tokenValue == null || tokenValue.isEmpty()) {
            log.warn("尝试吊销空 tokenValue");
            return;
        }
        revocationStore.revokeByTokenValue(tokenValue, reason != null ? reason : "手动吊销");
        log.info("Token 已吊销（tokenValue）");
    }

    public boolean isRevoked(String jwtId, String tokenValue) {
        return revocationStore.isRevoked(jwtId, tokenValue);
    }

    public void restoreToken(String jwtId) {
        if (jwtId == null || jwtId.isEmpty()) {
            log.warn("尝试恢复空 jwtId");
            return;
        }
        revocationStore.removeFromBlacklist(jwtId);
        log.info("Token 已从黑名单恢复: {}", jwtId);
    }

    public long getBlacklistSize() {
        return revocationStore.getBlacklistSize();
    }

    public void clearExpiredEntries(long maxAgeSeconds) {
        revocationStore.clearExpiredEntries(maxAgeSeconds);
    }

    public Map<String, Object> getBlacklistStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("size", getBlacklistSize());
        return stats;
    }
}
