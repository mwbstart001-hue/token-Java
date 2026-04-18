package com.example.tokenservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MemoryUserService implements UserService {
    
    private final Map<String, UserInfo> users = new ConcurrentHashMap<>();
    
    @Value("${token.auth.enabled:false}")
    private boolean authEnabled;
    
    @PostConstruct
    public void init() {
        users.put("user-123", new UserInfo("user-123", "test-user", "password123"));
        users.put("admin", new UserInfo("admin", "administrator", "admin123"));
        
        log.info("MemoryUserService initialized. Auth enabled: {}", authEnabled);
    }
    
    @Override
    public boolean authenticate(String userId, String password) {
        if (!authEnabled) {
            log.warn("Authentication is disabled! This is not recommended for production.");
            return true;
        }
        
        if (userId == null || password == null) {
            return false;
        }
        
        UserInfo user = users.get(userId);
        if (user == null) {
            log.warn("Authentication failed: user not found - userId: {}", userId);
            return false;
        }
        
        boolean authenticated = password.equals(user.getPassword());
        if (!authenticated) {
            log.warn("Authentication failed: invalid password - userId: {}", userId);
        }
        
        return authenticated;
    }
    
    @Override
    public String getUsername(String userId) {
        if (userId == null) {
            return null;
        }
        UserInfo user = users.get(userId);
        return user != null ? user.getUsername() : userId;
    }
    
    private static class UserInfo {
        private final String userId;
        private final String username;
        private final String password;
        
        public UserInfo(String userId, String username, String password) {
            this.userId = userId;
            this.username = username;
            this.password = password;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPassword() {
            return password;
        }
    }
}
