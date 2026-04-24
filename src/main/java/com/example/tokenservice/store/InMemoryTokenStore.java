package com.example.tokenservice.store;

import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@ConditionalOnProperty(name = "token.storage-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryTokenStore implements TokenStore {

    private final ConcurrentHashMap<String, Token> tokenMap = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public Token save(Token token) {
        lock.writeLock().lock();
        try {
            tokenMap.put(token.getTokenValue(), token);
            return token;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<Token> findByTokenValue(String tokenValue) {
        lock.readLock().lock();
        try {
            Token token = tokenMap.get(tokenValue);
            if (token != null && token.isExpired() && token.getStatus() == TokenStatus.ACTIVE) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    token = tokenMap.get(tokenValue);
                    if (token != null && token.isExpired() && token.getStatus() == TokenStatus.ACTIVE) {
                        token.setStatus(TokenStatus.EXPIRED);
                        tokenMap.put(tokenValue, token);
                    }
                } finally {
                    lock.writeLock().unlock();
                }
                lock.readLock().lock();
            }
            return Optional.ofNullable(token);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteByTokenValue(String tokenValue) {
        lock.writeLock().lock();
        try {
            tokenMap.remove(tokenValue);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean existsByTokenValue(String tokenValue) {
        lock.readLock().lock();
        try {
            return tokenMap.containsKey(tokenValue);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void updateStatus(String tokenValue, TokenStatus status) {
        lock.writeLock().lock();
        try {
            Token token = tokenMap.get(tokenValue);
            if (token != null) {
                token.setStatus(status);
                token.setUpdatedAt(LocalDateTime.now());
                tokenMap.put(tokenValue, token);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clearExpired() {
        lock.writeLock().lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            tokenMap.entrySet().removeIf(entry -> entry.getValue().getExpiresAt().isBefore(now));
        } finally {
            lock.writeLock().unlock();
        }
    }
}
