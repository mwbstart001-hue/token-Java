package com.example.tokenservice.store;

import java.util.Optional;

public interface TokenStore {
    
    void saveAccessToken(String userId, String token, long ttlMillis);
    
    void saveRefreshToken(String userId, String token, long ttlMillis);
    
    Optional<String> getAccessToken(String userId);
    
    Optional<String> getRefreshToken(String userId);
    
    void removeAccessToken(String userId);
    
    void removeRefreshToken(String userId);
    
    void addToBlacklist(String token, long ttlMillis);
    
    boolean isBlacklisted(String token);
    
    boolean validateAccessToken(String userId, String token);
    
    boolean validateRefreshToken(String userId, String token);
    
    void markRefreshTokenUsed(String oldToken, String newToken, String userId, long ttlMillis);
    
    boolean isRefreshTokenUsed(String token);
}
