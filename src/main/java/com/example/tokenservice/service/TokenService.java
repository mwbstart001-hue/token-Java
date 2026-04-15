package com.example.tokenservice.service;

import com.example.tokenservice.common.TraceContext;
import com.example.tokenservice.config.JwtConfig;
import com.example.tokenservice.dto.TokenResponse;
import com.example.tokenservice.dto.ValidationResult;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TokenService {
    
    private final JwtConfig jwtConfig;
    private final SecretKey signingKey;
    
    private final Map<String, String> userTokenMap = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenBlacklist = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenExpiryMap = new ConcurrentHashMap<>();
    
    @Autowired
    public TokenService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.signingKey = Keys.hmacShaKeyFor(
                jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }
    
    public TokenResponse generateToken(String userId, String username) {
        String traceId = TraceContext.getTraceId();
        
        log.info("[{}] Generating token for userId: {}, username: {}", 
                traceId, userId, maskUsername(username));
        
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtConfig.getExpiration());
        
        String token = Jwts.builder()
                .setSubject(userId)
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
        
        userTokenMap.put(userId, token);
        tokenExpiryMap.put(token, expiration.getTime());
        
        log.info("[{}] Token generated successfully for userId: {}, expiresAt: {}", 
                traceId, userId, expiration);
        
        return TokenResponse.builder()
                .token(token)
                .userId(userId)
                .issuedAt(LocalDateTime.ofInstant(now.toInstant(), ZoneId.systemDefault()))
                .expiresAt(LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()))
                .build();
    }
    
    public ValidationResult validateToken(String token) {
        String traceId = TraceContext.getTraceId();
        String tokenPreview = maskToken(token);
        
        log.info("[{}] Validating token: {}", traceId, tokenPreview);
        
        if (token == null || token.trim().isEmpty()) {
            log.warn("[{}] Token is null or empty", traceId);
            return ValidationResult.builder()
                    .valid(false)
                    .message("Token 为空或无效")
                    .build();
        }
        
        try {
            if (tokenBlacklist.containsKey(token)) {
                log.warn("[{}] Token is in blacklist: {}", traceId, tokenPreview);
                return ValidationResult.builder()
                        .valid(false)
                        .message("Token 已作废")
                        .build();
            }
            
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            String userId = claims.getSubject();
            
            String storedToken = userTokenMap.get(userId);
            if (storedToken == null || !token.equals(storedToken)) {
                log.warn("[{}] Token mismatch for userId: {}. Token may be expired or replaced.", 
                        traceId, userId);
                return ValidationResult.builder()
                        .valid(false)
                        .message("Token 已失效")
                        .build();
            }
            
            log.info("[{}] Token validated successfully for userId: {}", traceId, userId);
            
            return ValidationResult.builder()
                    .valid(true)
                    .userId(userId)
                    .message("Token 有效")
                    .build();
                    
        } catch (ExpiredJwtException e) {
            log.warn("[{}] Token expired: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
            return ValidationResult.builder()
                    .valid(false)
                    .message("Token 已过期")
                    .build();
        } catch (UnsupportedJwtException e) {
            log.warn("[{}] Unsupported JWT format: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
            return ValidationResult.builder()
                    .valid(false)
                    .message("不支持的 Token 格式")
                    .build();
        } catch (MalformedJwtException e) {
            log.warn("[{}] Malformed JWT: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
            return ValidationResult.builder()
                    .valid(false)
                    .message("无效的 Token 格式")
                    .build();
        } catch (SignatureException e) {
            log.warn("[{}] JWT signature validation failed: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
            return ValidationResult.builder()
                    .valid(false)
                    .message("Token 签名验证失败")
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("[{}] Invalid JWT argument: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
            return ValidationResult.builder()
                    .valid(false)
                    .message("Token 为空或无效")
                    .build();
        } catch (Exception e) {
            log.error("[{}] Unexpected error during token validation: {}, cause: {}", 
                    traceId, tokenPreview, e.getMessage(), e);
            return ValidationResult.builder()
                    .valid(false)
                    .message("Token 验证失败")
                    .build();
        }
    }
    
    public boolean revokeToken(String token) {
        String traceId = TraceContext.getTraceId();
        String tokenPreview = maskToken(token);
        
        log.info("[{}] Revoking token: {}", traceId, tokenPreview);
        
        if (token == null || token.trim().isEmpty()) {
            log.warn("[{}] Cannot revoke null or empty token", traceId);
            return false;
        }
        
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            String userId = claims.getSubject();
            Date expiration = claims.getExpiration();
            long ttl = expiration.getTime() - System.currentTimeMillis();
            
            if (ttl > 0) {
                tokenBlacklist.put(token, expiration.getTime());
                userTokenMap.remove(userId);
                tokenExpiryMap.remove(token);
                
                log.info("[{}] Token revoked successfully for userId: {}", traceId, userId);
                return true;
            } else {
                log.warn("[{}] Token already expired for userId: {}", traceId, userId);
                return false;
            }
        } catch (ExpiredJwtException e) {
            log.warn("[{}] Cannot revoke expired token: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("[{}] Failed to revoke token: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
            return false;
        }
    }
    
    public boolean revokeTokenByUserId(String userId) {
        String traceId = TraceContext.getTraceId();
        
        log.info("[{}] Revoking token by userId: {}", traceId, userId);
        
        String token = userTokenMap.get(userId);
        
        if (token != null) {
            Long expiryTime = tokenExpiryMap.get(token);
            if (expiryTime != null && expiryTime > System.currentTimeMillis()) {
                tokenBlacklist.put(token, expiryTime);
            }
            userTokenMap.remove(userId);
            tokenExpiryMap.remove(token);
            
            log.info("[{}] Token revoked successfully for userId: {}", traceId, userId);
            return true;
        }
        
        log.warn("[{}] No active token found for userId: {}", traceId, userId);
        return false;
    }
    
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    @Scheduled(fixedRate = 60000)
    public void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        int initialBlacklistSize = tokenBlacklist.size();
        int initialTokenCount = tokenExpiryMap.size();
        
        tokenBlacklist.entrySet().removeIf(entry -> entry.getValue() <= now);
        
        tokenExpiryMap.entrySet().removeIf(entry -> {
            if (entry.getValue() <= now) {
                userTokenMap.values().removeIf(v -> v.equals(entry.getKey()));
                return true;
            }
            return false;
        });
        
        int cleanedBlacklist = initialBlacklistSize - tokenBlacklist.size();
        int cleanedTokens = initialTokenCount - tokenExpiryMap.size();
        
        if (cleanedBlacklist > 0 || cleanedTokens > 0) {
            log.info("Cleaned expired tokens: blacklist={}, active={}, remaining users={}, remaining blacklist={}",
                    cleanedBlacklist, cleanedTokens, userTokenMap.size(), tokenBlacklist.size());
        }
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
    
    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }
}
