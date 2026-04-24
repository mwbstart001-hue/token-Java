package com.example.tokenservice.service;

import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.dto.TokenInfo;
import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class TokenService {

    private final TokenStore tokenStore;
    private final TokenProperties tokenProperties;
    private volatile SecretKey secretKey;

    public TokenService(TokenStore tokenStore, TokenProperties tokenProperties) {
        this.tokenStore = tokenStore;
        this.tokenProperties = tokenProperties;
    }

    private SecretKey getSecretKey() {
        if (secretKey == null) {
            synchronized (this) {
                if (secretKey == null) {
                    secretKey = Keys.hmacShaKeyFor(
                        tokenProperties.getSecret().getBytes(StandardCharsets.UTF_8)
                    );
                }
            }
        }
        return secretKey;
    }

    public String generateToken(String userId, String subject, Long expireSeconds) {
        long actualExpireSeconds = expireSeconds != null ? expireSeconds : tokenProperties.getDefaultExpireSeconds();
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(actualExpireSeconds);

        String jwtId = UUID.randomUUID().toString();
        
        String tokenValue = Jwts.builder()
                .setId(jwtId)
                .setSubject(subject != null ? subject : userId)
                .setIssuer("token-service")
                .setIssuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .setExpiration(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()))
                .claim("userId", userId)
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();

        Token token = new Token();
        token.setTokenValue(tokenValue);
        token.setUserId(userId);
        token.setSubject(subject);
        token.setIssuedAt(now);
        token.setExpiresAt(expiresAt);
        token.setStatus(TokenStatus.ACTIVE);

        tokenStore.save(token);

        return tokenValue;
    }

    public boolean validateToken(String tokenValue) {
        Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);
        
        if (!tokenOpt.isPresent()) {
            return false;
        }

        Token token = tokenOpt.get();
        
        if (!token.isValid()) {
            return false;
        }

        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSecretKey())
                    .build()
                    .parseClaimsJws(tokenValue);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            tokenStore.updateStatus(tokenValue, TokenStatus.INVALIDATED);
            return false;
        }
    }

    public Optional<TokenInfo> getTokenInfo(String tokenValue) {
        Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);
        
        if (!tokenOpt.isPresent()) {
            return Optional.empty();
        }

        Token token = tokenOpt.get();
        TokenInfo info = new TokenInfo();
        info.setTokenValue(token.getTokenValue());
        info.setUserId(token.getUserId());
        info.setSubject(token.getSubject());
        info.setIssuedAt(token.getIssuedAt());
        info.setExpiresAt(token.getExpiresAt());
        info.setStatus(token.getStatus());
        info.setValid(token.isValid());

        return Optional.of(info);
    }

    public boolean invalidateToken(String tokenValue) {
        Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);
        
        if (!tokenOpt.isPresent()) {
            return false;
        }

        Token token = tokenOpt.get();
        if (token.getStatus() != TokenStatus.ACTIVE) {
            return false;
        }

        tokenStore.updateStatus(tokenValue, TokenStatus.INVALIDATED);
        return true;
    }

    public void clearExpiredTokens() {
        tokenStore.clearExpired();
    }
}
