package com.example.tokenservice.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
        executeWithFallback(() -> {
            String key = ACCESS_TOKEN_KEY + userId;
            redisTemplate.opsForValue().set(key, token, ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Saved access token to Redis for userId: {}, ttl: {}ms", userId, ttlMillis);
            return null;
        }, "saveAccessToken", userId);
    }
    
    @Override
    public void saveRefreshToken(String userId, String token, long ttlMillis) {
        executeWithFallback(() -> {
            String key = REFRESH_TOKEN_KEY + userId;
            redisTemplate.opsForValue().set(key, token, ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Saved refresh token to Redis for userId: {}, ttl: {}ms", userId, ttlMillis);
            return null;
        }, "saveRefreshToken", userId);
    }
    
    @Override
    public Optional<String> getAccessToken(String userId) {
        return executeWithFallback(() -> {
            String key = ACCESS_TOKEN_KEY + userId;
            Object token = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(token != null ? token.toString() : null);
        }, "getAccessToken", userId, Optional.empty());
    }
    
    @Override
    public Optional<String> getRefreshToken(String userId) {
        return executeWithFallback(() -> {
            String key = REFRESH_TOKEN_KEY + userId;
            Object token = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(token != null ? token.toString() : null);
        }, "getRefreshToken", userId, Optional.empty());
    }
    
    @Override
    public void removeAccessToken(String userId) {
        executeWithFallback(() -> {
            String key = ACCESS_TOKEN_KEY + userId;
            redisTemplate.delete(key);
            log.debug("Removed access token from Redis for userId: {}", userId);
            return null;
        }, "removeAccessToken", userId);
    }
    
    @Override
    public void removeRefreshToken(String userId) {
        executeWithFallback(() -> {
            String key = REFRESH_TOKEN_KEY + userId;
            redisTemplate.delete(key);
            log.debug("Removed refresh token from Redis for userId: {}", userId);
            return null;
        }, "removeRefreshToken", userId);
    }
    
    @Override
    public void addToBlacklist(String token, long ttlMillis) {
        executeWithFallback(() -> {
            String key = BLACKLIST_KEY + token;
            redisTemplate.opsForValue().set(key, true, ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Added token to blacklist in Redis: {}", maskToken(token));
            return null;
        }, "addToBlacklist", maskToken(token));
    }
    
    @Override
    public boolean isBlacklisted(String token) {
        return executeWithFallback(() -> {
            String key = BLACKLIST_KEY + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        }, "isBlacklisted", maskToken(token), false);
    }
    
    @Override
    public boolean validateAccessToken(String userId, String token) {
        return executeWithFallback(() -> {
            Optional<String> storedTokenOpt = getAccessToken(userId);
            if (!storedTokenOpt.isPresent() || !token.equals(storedTokenOpt.get())) {
                return false;
            }
            String key = ACCESS_TOKEN_KEY + userId;
            Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            return ttl != null && ttl > 0;
        }, "validateAccessToken", userId, false);
    }
    
    @Override
    public boolean validateRefreshToken(String userId, String token) {
        return executeWithFallback(() -> {
            Optional<String> storedTokenOpt = getRefreshToken(userId);
            if (!storedTokenOpt.isPresent() || !token.equals(storedTokenOpt.get())) {
                return false;
            }
            String key = REFRESH_TOKEN_KEY + userId;
            Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            return ttl != null && ttl > 0;
        }, "validateRefreshToken", userId, false);
    }
    
    @Override
    public void markRefreshTokenUsed(String oldToken, String newToken, String userId, long ttlMillis) {
        executeWithFallback(() -> {
            String key = USED_REFRESH_TOKEN_KEY + oldToken;
            redisTemplate.opsForValue().set(key, newToken, ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Marked refresh token as used in Redis: {}", maskToken(oldToken));
            return null;
        }, "markRefreshTokenUsed", maskToken(oldToken));
    }
    
    @Override
    public boolean isRefreshTokenUsed(String token) {
        return executeWithFallback(() -> {
            String key = USED_REFRESH_TOKEN_KEY + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        }, "isRefreshTokenUsed", maskToken(token), false);
    }
    
    private <T> T executeWithFallback(Supplier<T> supplier, String operation, String context) {
        try {
            return supplier.get();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed during {} for context: {}, cause: {}", 
                    operation, context, e.getMessage(), e);
            throw new IllegalStateException("Redis 连接失败，请稍后重试", e);
        } catch (Exception e) {
            log.error("Error during {} for context: {}, cause: {}", 
                    operation, context, e.getMessage(), e);
            throw new IllegalStateException("操作失败: " + operation, e);
        }
    }
    
    private <T> T executeWithFallback(Supplier<T> supplier, String operation, String context, T fallback) {
        try {
            return supplier.get();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed during {} for context: {}, using fallback. cause: {}", 
                    operation, context, e.getMessage(), e);
            return fallback;
        } catch (Exception e) {
            log.error("Error during {} for context: {}, using fallback. cause: {}", 
                    operation, context, e.getMessage(), e);
            return fallback;
        }
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}
