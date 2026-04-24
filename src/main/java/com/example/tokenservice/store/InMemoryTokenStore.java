package com.example.tokenservice.store;

import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "token.storage-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryTokenStore implements TokenStore {

    private final ConcurrentHashMap<String, Token> tokenMap = new ConcurrentHashMap<>();

    @Override
    public Token save(Token token) {
        tokenMap.put(token.getTokenValue(), token);
        return token;
    }

    @Override
    public Optional<Token> findByTokenValue(String tokenValue) {
        Token token = tokenMap.computeIfPresent(tokenValue, (k, existingToken) -> {
            if (existingToken.isExpired() && existingToken.getStatus() == TokenStatus.ACTIVE) {
                existingToken.setStatus(TokenStatus.EXPIRED);
                existingToken.setUpdatedAt(LocalDateTime.now());
            }
            return existingToken;
        });
        return Optional.ofNullable(token);
    }

    @Override
    public void deleteByTokenValue(String tokenValue) {
        tokenMap.remove(tokenValue);
    }

    @Override
    public boolean existsByTokenValue(String tokenValue) {
        return tokenMap.containsKey(tokenValue);
    }

    @Override
    public void updateStatus(String tokenValue, TokenStatus status) {
        tokenMap.computeIfPresent(tokenValue, (k, existingToken) -> {
            existingToken.setStatus(status);
            existingToken.setUpdatedAt(LocalDateTime.now());
            return existingToken;
        });
    }

    @Override
    public void clearExpired() {
        LocalDateTime now = LocalDateTime.now();
        tokenMap.entrySet().removeIf(entry -> entry.getValue().getExpiresAt().isBefore(now));
    }
}
