package com.example.tokenservice.store;

import com.example.tokenservice.common.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@ConditionalOnProperty(name = "token.storage.type", havingValue = "redis")
public class RedisTokenStore implements TokenStore {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String KEY_PREFIX = "token:";
    
    public RedisTokenStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    private String getTenantPrefix() {
        String tenantId = TenantContext.getTenantId();
        return KEY_PREFIX + tenantId + ":";
    }
    
    private String getAccessTokenKey(String userId) {
        return getTenantPrefix() + "access:" + userId;
    }
    
    private String getRefreshTokenKey(String userId) {
        return getTenantPrefix() + "refresh:" + userId;
    }
    
    private String getBlacklistKey(String token) {
        return getTenantPrefix() + "blacklist:" + token;
    }
    
    private String getUsedRefreshTokenKey(String token) {
        return getTenantPrefix() + "used_refresh:" + token;
    }
    
    @Override
    public void saveAccessToken(String userId, String token, long ttlMillis) {
        executeWithFallback(() -> {
            String key = getAccessTokenKey(userId);
            redisTemplate.opsForValue().set(key, token, ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Saved access token to Redis for userId: {}, tenant: {}, ttl: {}ms", 
                    userId, TenantContext.getTenantId(), ttlMillis);
            return null;
        }, "saveAccessToken", userId);
    }
    
    @Override
    public void saveRefreshToken(String userId, String token, long ttlMillis) {
        executeWithFallback(() -> {
            String key = getRefreshTokenKey(userId);
            redisTemplate.opsForValue().set(key, token, ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Saved refresh token to Redis for userId: {}, tenant: {}, ttl: {}ms", 
                    userId, TenantContext.getTenantId(), ttlMillis);
            return null;
        }, "saveRefreshToken", userId);
    }
    
    @Override
    public Optional<String> getAccessToken(String userId) {
        return executeWithFallback(() -> {
            String key = getAccessTokenKey(userId);
            Object token = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(token != null ? token.toString() : null);
        }, "getAccessToken", userId, Optional.empty());
    }
    
    @Override
    public Optional<String> getRefreshToken(String userId) {
        return executeWithFallback(() -> {
            String key = getRefreshTokenKey(userId);
            Object token = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(token != null ? token.toString() : null);
        }, "getRefreshToken", userId, Optional.empty());
    }
    
    @Override
    public void removeAccessToken(String userId) {
        executeWithFallback(() -> {
            String key = getAccessTokenKey(userId);
            redisTemplate.delete(key);
            log.debug("Removed access token from Redis for userId: {}, tenant: {}", 
                    userId, TenantContext.getTenantId());
            return null;
        }, "removeAccessToken", userId);
    }
    
    @Override
    public void removeRefreshToken(String userId) {
        executeWithFallback(() -> {
            String key = getRefreshTokenKey(userId);
            redisTemplate.delete(key);
            log.debug("Removed refresh token from Redis for userId: {}, tenant: {}", 
                    userId, TenantContext.getTenantId());
            return null;
        }, "removeRefreshToken", userId);
    }
    
    @Override
    public void addToBlacklist(String token, long ttlMillis) {
        executeWithFallback(() -> {
            String key = getBlacklistKey(token);
            redisTemplate.opsForValue().set(key, true, ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Added token to blacklist in Redis: {}, tenant: {}", 
                    maskToken(token), TenantContext.getTenantId());
            return null;
        }, "addToBlacklist", maskToken(token));
    }
    
    @Override
    public boolean isBlacklisted(String token) {
        return executeWithFallback(() -> {
            String key = getBlacklistKey(token);
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
            String key = getAccessTokenKey(userId);
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
            String key = getRefreshTokenKey(userId);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            return ttl != null && ttl > 0;
        }, "validateRefreshToken", userId, false);
    }
    
    @Override
    public void markRefreshTokenUsed(String oldToken, String newToken, String userId, long ttlMillis) {
        executeWithFallback(() -> {
            String key = getUsedRefreshTokenKey(oldToken);
            redisTemplate.opsForValue().set(key, newToken, ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Marked refresh token as used in Redis: {}, tenant: {}", 
                    maskToken(oldToken), TenantContext.getTenantId());
            return null;
        }, "markRefreshTokenUsed", maskToken(oldToken));
    }
    
    @Override
    public boolean isRefreshTokenUsed(String token) {
        return executeWithFallback(() -> {
            String key = getUsedRefreshTokenKey(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        }, "isRefreshTokenUsed", maskToken(token), false);
    }
    
    private <T> T executeWithFallback(Supplier<T> supplier, String operation, String context) {
        try {
            return supplier.get();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed during {} for context: {}, tenant: {}, cause: {}", 
                    operation, context, TenantContext.getTenantId(), e.getMessage(), e);
            throw new IllegalStateException("Redis 连接失败，请稍后重试", e);
        } catch (Exception e) {
            log.error("Error during {} for context: {}, tenant: {}, cause: {}", 
                    operation, context, TenantContext.getTenantId(), e.getMessage(), e);
            throw new IllegalStateException("操作失败: " + operation, e);
        }
    }
    
    private <T> T executeWithFallback(Supplier<T> supplier, String operation, String context, T fallback) {
        try {
            return supplier.get();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed during {} for context: {}, tenant: {}, using fallback. cause: {}", 
                    operation, context, TenantContext.getTenantId(), e.getMessage(), e);
            return fallback;
        } catch (Exception e) {
            log.error("Error during {} for context: {}, tenant: {}, using fallback. cause: {}", 
                    operation, context, TenantContext.getTenantId(), e.getMessage(), e);
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
