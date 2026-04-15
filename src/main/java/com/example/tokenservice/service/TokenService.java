package com.example.tokenservice.service;

import com.example.tokenservice.config.JwtConfig;
import com.example.tokenservice.dto.TokenResponse;
import com.example.tokenservice.dto.ValidationResult;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {
    
    private final JwtConfig jwtConfig;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    private static final String USER_TOKEN_PREFIX = "token:user:";
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    public TokenResponse generateToken(String userId, String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtConfig.getExpiration());
        
        String token = Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
        
        String userTokenKey = USER_TOKEN_PREFIX + userId;
        redisTemplate.opsForValue().set(userTokenKey, token, jwtConfig.getExpiration(), TimeUnit.MILLISECONDS);
        
        return TokenResponse.builder()
                .token(token)
                .userId(userId)
                .issuedAt(LocalDateTime.ofInstant(now.toInstant(), ZoneId.systemDefault()))
                .expiresAt(LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()))
                .build();
    }
    
    public ValidationResult validateToken(String token) {
        try {
            String blacklistKey = TOKEN_BLACKLIST_PREFIX + token;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
                return ValidationResult.builder()
                        .valid(false)
                        .message("Token 已作废")
                        .build();
            }
            
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            String userId = claims.getSubject();
            
            String userTokenKey = USER_TOKEN_PREFIX + userId;
            Object storedToken = redisTemplate.opsForValue().get(userTokenKey);
            if (storedToken == null || !token.equals(storedToken.toString())) {
                return ValidationResult.builder()
                        .valid(false)
                        .message("Token 已失效")
                        .build();
            }
            
            return ValidationResult.builder()
                    .valid(true)
                    .userId(userId)
                    .message("Token 有效")
                    .build();
                    
        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期: {}", e.getMessage());
            return ValidationResult.builder()
                    .valid(false)
                    .message("Token 已过期")
                    .build();
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的 Token 格式: {}", e.getMessage());
            return ValidationResult.builder()
                    .valid(false)
                    .message("不支持的 Token 格式")
                    .build();
        } catch (MalformedJwtException e) {
            log.warn("无效的 Token 格式: {}", e.getMessage());
            return ValidationResult.builder()
                    .valid(false)
                    .message("无效的 Token 格式")
                    .build();
        } catch (SecurityException e) {
            log.warn("Token 签名验证失败: {}", e.getMessage());
            return ValidationResult.builder()
                    .valid(false)
                    .message("Token 签名验证失败")
                    .build();
        } catch (Exception e) {
            log.error("Token 验证失败: {}", e.getMessage());
            return ValidationResult.builder()
                    .valid(false)
                    .message("Token 验证失败: " + e.getMessage())
                    .build();
        }
    }
    
    public boolean revokeToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            String userId = claims.getSubject();
            Date expiration = claims.getExpiration();
            long ttl = expiration.getTime() - System.currentTimeMillis();
            
            if (ttl > 0) {
                String blacklistKey = TOKEN_BLACKLIST_PREFIX + token;
                redisTemplate.opsForValue().set(blacklistKey, true, ttl, TimeUnit.MILLISECONDS);
                
                String userTokenKey = USER_TOKEN_PREFIX + userId;
                redisTemplate.delete(userTokenKey);
                
                log.info("Token 已作废, userId: {}", userId);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("作废 Token 失败: {}", e.getMessage());
            return false;
        }
    }
    
    public boolean revokeTokenByUserId(String userId) {
        String userTokenKey = USER_TOKEN_PREFIX + userId;
        Object token = redisTemplate.opsForValue().get(userTokenKey);
        
        if (token != null) {
            String blacklistKey = TOKEN_BLACKLIST_PREFIX + token.toString();
            redisTemplate.opsForValue().set(blacklistKey, true, jwtConfig.getExpiration(), TimeUnit.MILLISECONDS);
            redisTemplate.delete(userTokenKey);
            log.info("用户 {} 的 Token 已作废", userId);
            return true;
        }
        
        return false;
    }
    
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
