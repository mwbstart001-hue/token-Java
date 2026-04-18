package com.example.tokenservice.store;

import com.example.tokenservice.common.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "token.storage.type", havingValue = "memory", matchIfMissing = true)
public class MemoryTokenStore implements TokenStore {
    
    private final Map<String, Map<String, String>> accessTokenMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> accessTokenExpiryMap = new ConcurrentHashMap<>();
    
    private final Map<String, Map<String, String>> refreshTokenMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> refreshTokenExpiryMap = new ConcurrentHashMap<>();
    
    private final Map<String, Map<String, Long>> tokenBlacklist = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> usedRefreshTokenMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> usedRefreshTokenExpiryMap = new ConcurrentHashMap<>();
    
    private Map<String, String> getTenantMap(Map<String, Map<String, String>> outerMap) {
        String tenantId = TenantContext.getTenantId();
        return outerMap.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }
    
    private Map<String, Long> getTenantLongMap(Map<String, Map<String, Long>> outerMap) {
        String tenantId = TenantContext.getTenantId();
        return outerMap.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }
    
    @Override
    public void saveAccessToken(String userId, String token, long ttlMillis) {
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        getTenantMap(accessTokenMap).put(userId, token);
        getTenantLongMap(accessTokenExpiryMap).put(token, expiryTime);
        log.debug("Saved access token for userId: {}, tenant: {}, expiry: {}", 
                userId, TenantContext.getTenantId(), expiryTime);
    }
    
    @Override
    public void saveRefreshToken(String userId, String token, long ttlMillis) {
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        getTenantMap(refreshTokenMap).put(userId, token);
        getTenantLongMap(refreshTokenExpiryMap).put(token, expiryTime);
        log.debug("Saved refresh token for userId: {}, tenant: {}, expiry: {}", 
                userId, TenantContext.getTenantId(), expiryTime);
    }
    
    @Override
    public Optional<String> getAccessToken(String userId) {
        return Optional.ofNullable(getTenantMap(accessTokenMap).get(userId));
    }
    
    @Override
    public Optional<String> getRefreshToken(String userId) {
        return Optional.ofNullable(getTenantMap(refreshTokenMap).get(userId));
    }
    
    @Override
    public void removeAccessToken(String userId) {
        String token = getTenantMap(accessTokenMap).remove(userId);
        if (token != null) {
            getTenantLongMap(accessTokenExpiryMap).remove(token);
        }
        log.debug("Removed access token for userId: {}, tenant: {}", 
                userId, TenantContext.getTenantId());
    }
    
    @Override
    public void removeRefreshToken(String userId) {
        String token = getTenantMap(refreshTokenMap).remove(userId);
        if (token != null) {
            getTenantLongMap(refreshTokenExpiryMap).remove(token);
        }
        log.debug("Removed refresh token for userId: {}, tenant: {}", 
                userId, TenantContext.getTenantId());
    }
    
    @Override
    public void addToBlacklist(String token, long ttlMillis) {
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        getTenantLongMap(tokenBlacklist).put(token, expiryTime);
        log.debug("Added token to blacklist: {}, tenant: {}", 
                maskToken(token), TenantContext.getTenantId());
    }
    
    @Override
    public boolean isBlacklisted(String token) {
        return getTenantLongMap(tokenBlacklist).containsKey(token);
    }
    
    @Override
    public boolean validateAccessToken(String userId, String token) {
        Map<String, String> tenantAccessMap = getTenantMap(accessTokenMap);
        Map<String, Long> tenantAccessExpiryMap = getTenantLongMap(accessTokenExpiryMap);
        
        String storedToken = tenantAccessMap.get(userId);
        if (storedToken == null || !token.equals(storedToken)) {
            return false;
        }
        Long expiry = tenantAccessExpiryMap.get(token);
        return expiry != null && expiry > System.currentTimeMillis();
    }
    
    @Override
    public boolean validateRefreshToken(String userId, String token) {
        Map<String, String> tenantRefreshMap = getTenantMap(refreshTokenMap);
        Map<String, Long> tenantRefreshExpiryMap = getTenantLongMap(refreshTokenExpiryMap);
        
        String storedToken = tenantRefreshMap.get(userId);
        if (storedToken == null || !token.equals(storedToken)) {
            return false;
        }
        Long expiry = tenantRefreshExpiryMap.get(token);
        return expiry != null && expiry > System.currentTimeMillis();
    }
    
    @Override
    public void markRefreshTokenUsed(String oldToken, String newToken, String userId, long ttlMillis) {
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        getTenantMap(usedRefreshTokenMap).put(oldToken, newToken);
        getTenantLongMap(usedRefreshTokenExpiryMap).put(oldToken, expiryTime);
        log.debug("Marked refresh token as used: {}, tenant: {}", 
                maskToken(oldToken), TenantContext.getTenantId());
    }
    
    @Override
    public boolean isRefreshTokenUsed(String token) {
        return getTenantMap(usedRefreshTokenMap).containsKey(token);
    }
    
    @Scheduled(fixedRate = 60000)
    public void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        
        for (String tenantId : tokenBlacklist.keySet()) {
            Map<String, Long> blacklist = tokenBlacklist.get(tenantId);
            if (blacklist != null) {
                blacklist.entrySet().removeIf(entry -> entry.getValue() <= now);
            }
        }
        
        for (String tenantId : usedRefreshTokenMap.keySet()) {
            Map<String, String> usedTokens = usedRefreshTokenMap.get(tenantId);
            Map<String, Long> usedExpiry = usedRefreshTokenExpiryMap.get(tenantId);
            if (usedTokens != null && usedExpiry != null) {
                usedTokens.keySet().removeIf(token -> {
                    Long expiry = usedExpiry.get(token);
                    if (expiry != null && expiry <= now) {
                        usedExpiry.remove(token);
                        return true;
                    }
                    return false;
                });
            }
        }
        
        for (String tenantId : accessTokenExpiryMap.keySet()) {
            Map<String, Long> expiryMap = accessTokenExpiryMap.get(tenantId);
            Map<String, String> tokenMap = accessTokenMap.get(tenantId);
            if (expiryMap != null && tokenMap != null) {
                expiryMap.entrySet().removeIf(entry -> {
                    if (entry.getValue() <= now) {
                        tokenMap.values().removeIf(v -> v.equals(entry.getKey()));
                        return true;
                    }
                    return false;
                });
            }
        }
        
        for (String tenantId : refreshTokenExpiryMap.keySet()) {
            Map<String, Long> expiryMap = refreshTokenExpiryMap.get(tenantId);
            Map<String, String> tokenMap = refreshTokenMap.get(tenantId);
            if (expiryMap != null && tokenMap != null) {
                expiryMap.entrySet().removeIf(entry -> {
                    if (entry.getValue() <= now) {
                        tokenMap.values().removeIf(v -> v.equals(entry.getKey()));
                        return true;
                    }
                    return false;
                });
            }
        }
        
        log.debug("Cleaned expired tokens in memory store");
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}
