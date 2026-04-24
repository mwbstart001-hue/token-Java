package com.example.tokenservice.model;

import java.util.Optional;

public interface TokenStore {

    Token save(Token token);

    Optional<Token> findByTokenValue(String tokenValue);

    void deleteByTokenValue(String tokenValue);

    boolean existsByTokenValue(String tokenValue);

    void updateStatus(String tokenValue, TokenStatus status);

    void clearExpired();
}
