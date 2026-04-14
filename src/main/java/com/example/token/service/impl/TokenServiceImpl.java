package com.example.token.service.impl;

import com.example.token.model.TokenInfo;
import com.example.token.service.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenServiceImpl implements TokenService {

    @Value("${token.default-expire-seconds:3600}")
    private Long defaultExpireSeconds;

    private final Map<String, TokenInfo> tokenStore = new ConcurrentHashMap<>();

    @Override
    public TokenInfo generateToken(String userId, Long expireSeconds) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        long actualExpireSeconds = expireSeconds != null ? expireSeconds : defaultExpireSeconds;
        LocalDateTime expireTime = now.plusSeconds(actualExpireSeconds);

        TokenInfo tokenInfo = new TokenInfo(token, userId, now, expireTime);
        tokenStore.put(token, tokenInfo);
        return tokenInfo;
    }

    @Override
    public Optional<TokenInfo> validateToken(String token) {
        TokenInfo tokenInfo = tokenStore.get(token);
        if (tokenInfo == null) {
            return Optional.empty();
        }
        if (!tokenInfo.isValid()) {
            return Optional.empty();
        }
        if (tokenInfo.isExpired()) {
            tokenStore.remove(token);
            return Optional.empty();
        }
        return Optional.of(tokenInfo);
    }

    @Override
    public boolean invalidateToken(String token) {
        TokenInfo tokenInfo = tokenStore.get(token);
        if (tokenInfo != null && tokenInfo.isValid()) {
            tokenInfo.setValid(false);
            return true;
        }
        return false;
    }

    @Override
    public Optional<TokenInfo> getTokenInfo(String token) {
        return Optional.ofNullable(tokenStore.get(token));
    }
}
