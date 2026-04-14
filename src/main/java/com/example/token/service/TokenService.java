package com.example.token.service;

import com.example.token.model.TokenInfo;

import java.util.Optional;

public interface TokenService {

    TokenInfo generateToken(String userId, Long expireSeconds);

    Optional<TokenInfo> validateToken(String token);

    boolean invalidateToken(String token);

    Optional<TokenInfo> getTokenInfo(String token);
}
