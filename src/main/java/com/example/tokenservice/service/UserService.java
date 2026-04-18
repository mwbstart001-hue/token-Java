package com.example.tokenservice.service;

public interface UserService {
    
    boolean authenticate(String userId, String password);
    
    String getUsername(String userId);
}
