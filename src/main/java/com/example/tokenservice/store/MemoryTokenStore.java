package com.example.tokenservice.store;

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
    
    private final Map<String, String> accessTokenMap = new ConcurrentHashMap<>();
    private final Map<String, Long> accessTokenExpiryMap = new ConcurrentHashMap<>();
    
    private final Map<String, String> refreshTokenMap = new ConcurrentHashMap<>();
    private final Map<String, Long> refreshTokenExpiryMap = new ConcurrentHashMap<>();
    
    private final Map<String, Long> tokenBlacklist = new ConcurrentHashMap<>();
    private final Map<String, String> usedRefreshTokenMap = new ConcurrentHashMap<>();
    private final Map<String, Long> usedRefreshTokenExpiryMap = new ConcurrentHashMap<>();
    
    @Override
    public void saveAccessToken(String userId, String token, long ttlMillis) {
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        accessTokenMap.put(userId, token);
        accessTokenExpiryMap.put(token, expiryTime);
        log.debug("Saved access token for userId: {}, expiry: {}", userId, expiryTime);
    }
    
    @Override
    public void saveRefreshToken(String userId, String token, long ttlMillis) {
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        refreshTokenMap.put(userId, token);
        refreshTokenExpiryMap.put(token, expiryTime);
        log.debug("Saved refresh token for userId: {}, expiry: {}", userId, expiryTime);
    }
    
    @Override
    public Optional<String> getAccessToken(String userId) {
        return Optional.ofNullable(accessTokenMap.get(userId));
    }
    
    @Override
    public Optional<String> getRefreshToken(String userId) {
        return Optional.ofNullable(refreshTokenMap.get(userId));
    }
    
    @Override
    public void removeAccessToken(String userId) {
        String token = accessTokenMap.remove(userId);
        if (token != null) {
            accessTokenExpiryMap.remove(token);
        }
        log.debug("Removed access token for userId: {}", userId);
    }
    
    @Override
    public void removeRefreshToken(String userId) {
        String token = refreshTokenMap.remove(userId);
        if (token != null) {
            refreshTokenExpiryMap.remove(token);
        }
        log.debug("Removed refresh token for userId: {}", userId);
    }
    
    @Override
    public void addToBlacklist(String token, long ttlMillis) {
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        tokenBlacklist.put(token, expiryTime);
        log.debug("Added token to blacklist: {}", maskToken(token));
    }
    
    @Override
    public boolean isBlacklisted(String token) {
        return tokenBlacklist.containsKey(token);
    }
    
    @Override
    public boolean validateAccessToken(String userId, String token) {
        String storedToken = accessTokenMap.get(userId);
        if (storedToken == null || !token.equals(storedToken)) {
            return false;
        }
        Long expiry = accessTokenExpiryMap.get(token);
        return expiry != null && expiry > System.currentTimeMillis();
    }
    
    @Override
    public boolean validateRefreshToken(String userId, String token) {
        String storedToken = refreshTokenMap.get(userId);
        if (storedToken == null || !token.equals(storedToken)) {
            return false;
        }
        Long expiry = refreshTokenExpiryMap.get(token);
        return expiry != null && expiry > System.currentTimeMillis();
    }
    
    @Override
    public void markRefreshTokenUsed(String oldToken, String newToken, String userId, long ttlMillis) {
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        usedRefreshTokenMap.put(oldToken, newToken);
        usedRefreshTokenExpiryMap.put(oldToken, expiryTime);
        log.debug("Marked refresh token as used: {}", maskToken(oldToken));
    }
    
    @Override
    public boolean isRefreshTokenUsed(String token) {
        return usedRefreshTokenMap.containsKey(token);
    }
    
    @Scheduled(fixedRate = 60000)
    public void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        
        tokenBlacklist.entrySet().removeIf(entry -> entry.getValue() <= now);
        
        usedRefreshTokenMap.keySet().removeIf(token -> {
            Long expiry = usedRefreshTokenExpiryMap.get(token);
            if (expiry != null && expiry <= now) {
                usedRefreshTokenExpiryMap.remove(token);
                return true;
            }
            return false;
        });
        
        accessTokenExpiryMap.entrySet().removeIf(entry -> {
            if (entry.getValue() <= now) {
                accessTokenMap.values().removeIf(v -> v.equals(entry.getKey()));
                return true;
            }
            return false;
        });
        
        refreshTokenExpiryMap.entrySet().removeIf(entry -> {
            if (entry.getValue() <= now) {
                refreshTokenMap.values().removeIf(v -> v.equals(entry.getKey()));
                return true;
            }
            return false;
        });
        
        log.debug("Cleaned expired tokens in memory store");
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}
