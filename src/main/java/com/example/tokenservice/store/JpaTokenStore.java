package com.example.tokenservice.store;

import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import com.example.tokenservice.repository.TokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@ConditionalOnProperty(name = "token.storage-type", havingValue = "database")
public class JpaTokenStore implements TokenStore {

    private final TokenRepository tokenRepository;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public JpaTokenStore(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Override
    @Transactional
    public Token save(Token token) {
        lock.writeLock().lock();
        try {
            return tokenRepository.save(token);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Token> findByTokenValue(String tokenValue) {
        lock.readLock().lock();
        try {
            Optional<Token> tokenOpt = tokenRepository.findByTokenValue(tokenValue);
            if (tokenOpt.isPresent()) {
                Token token = tokenOpt.get();
                if (token.isExpired() && token.getStatus() == TokenStatus.ACTIVE) {
                    lock.readLock().unlock();
                    lock.writeLock().lock();
                    try {
                        tokenOpt = tokenRepository.findByTokenValue(tokenValue);
                        if (tokenOpt.isPresent()) {
                            token = tokenOpt.get();
                            if (token.isExpired() && token.getStatus() == TokenStatus.ACTIVE) {
                                token.setStatus(TokenStatus.EXPIRED);
                                tokenRepository.save(token);
                            }
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                    lock.readLock().lock();
                }
            }
            return tokenOpt;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    @Transactional
    public void deleteByTokenValue(String tokenValue) {
        lock.writeLock().lock();
        try {
            tokenRepository.deleteByTokenValue(tokenValue);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTokenValue(String tokenValue) {
        lock.readLock().lock();
        try {
            return tokenRepository.existsByTokenValue(tokenValue);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    @Transactional
    public void updateStatus(String tokenValue, TokenStatus status) {
        lock.writeLock().lock();
        try {
            tokenRepository.updateStatus(tokenValue, status, LocalDateTime.now());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    @Transactional
    public boolean updateStatusIfActive(String tokenValue, TokenStatus status) {
        lock.writeLock().lock();
        try {
            int updatedCount = tokenRepository.updateStatusIfActive(tokenValue, status, LocalDateTime.now());
            return updatedCount > 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    @Transactional
    public void clearExpired() {
        lock.writeLock().lock();
        try {
            tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        } finally {
            lock.writeLock().unlock();
        }
    }
}
