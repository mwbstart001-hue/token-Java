package com.example.tokenservice.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "token.storage.type", havingValue = "redis")
public class RedisTokenStore implements TokenStore {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String KEY_PREFIX = "token:";
    private static final String ACCESS_TOKEN_KEY = KEY_PREFIX + "access:";
    private static final String REFRESH_TOKEN_KEY = KEY_PREFIX + "refresh:";
    private static final String BLACKLIST_KEY = KEY_PREFIX + "blacklist:";
    private static final String USED_REFRESH_TOKEN_KEY = KEY_PREFIX + "used_refresh:";
    
    public RedisTokenStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public void saveAccessToken(String userId, String token, long ttlMillis) {
        String key = ACCESS_TOKEN_KEY + userId;
        redisTemplate.opsForValue().set(key, token, ttlMillis, TimeUnit.MILLISECONDS);
        log.debug("Saved access token to Redis for userId: {}, ttl: {}ms", userId, ttlMillis);
    }
    
    @Override
    public void saveRefreshToken(String userId, String token, long ttlMillis) {
        String key = REFRESH_TOKEN_KEY + userId;
        redisTemplate.opsForValue().set(key, token, ttlMillis, TimeUnit.MILLISECONDS);
        log.debug("Saved refresh token to Redis for userId: {}, ttl: {}ms", userId, ttlMillis);
    }
    
    @Override
    public Optional<String> getAccessToken(String userId) {
        String key = ACCESS_TOKEN_KEY + userId;
        Object token = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(token != null ? token.toString() : null);
    }
    
    @Override
    public Optional<String> getRefreshToken(String userId) {
        String key = REFRESH_TOKEN_KEY + userId;
        Object token = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(token != null ? token.toString() : null);
    }
    
    @Override
    public void removeAccessToken(String userId) {
        String key = ACCESS_TOKEN_KEY + userId;
        redisTemplate.delete(key);
        log.debug("Removed access token from Redis for userId: {}", userId);
    }
    
    @Override
    public void removeRefreshToken(String userId) {
        String key = REFRESH_TOKEN_KEY + userId;
        redisTemplate.delete(key);
        log.debug("Removed refresh token from Redis for userId: {}", userId);
    }
    
    @Override
    public void addToBlacklist(String token, long ttlMillis) {
        String key = BLACKLIST_KEY + token;
        redisTemplate.opsForValue().set(key, true, ttlMillis, TimeUnit.MILLISECONDS);
        log.debug("Added token to blacklist in Redis: {}", maskToken(token));
    }
    
    @Override
    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_KEY + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    @Override
    public boolean validateAccessToken(String userId, String token) {
        Optional<String> storedTokenOpt = getAccessToken(userId);
        if (!storedTokenOpt.isPresent() || !token.equals(storedTokenOpt.get())) {
            return false;
        }
        String key = ACCESS_TOKEN_KEY + userId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        return ttl != null && ttl > 0;
    }
    
    @Override
    public boolean validateRefreshToken(String userId, String token) {
        Optional<String> storedTokenOpt = getRefreshToken(userId);
        if (!storedTokenOpt.isPresent() || !token.equals(storedTokenOpt.get())) {
            return false;
        }
        String key = REFRESH_TOKEN_KEY + userId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        return ttl != null && ttl > 0;
    }
    
    @Override
    public void markRefreshTokenUsed(String oldToken, String newToken, String userId, long ttlMillis) {
        String key = USED_REFRESH_TOKEN_KEY + oldToken;
        redisTemplate.opsForValue().set(key, newToken, ttlMillis, TimeUnit.MILLISECONDS);
        log.debug("Marked refresh token as used in Redis: {}", maskToken(oldToken));
    }
    
    @Override
    public boolean isRefreshTokenUsed(String token) {
        String key = USED_REFRESH_TOKEN_KEY + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}
