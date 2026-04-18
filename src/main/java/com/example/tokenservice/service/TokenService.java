package com.example.tokenservice.service;

import com.example.tokenservice.common.TenantContext;
import com.example.tokenservice.common.TraceContext;
import com.example.tokenservice.config.JwtConfig;
import com.example.tokenservice.dto.TokenPair;
import com.example.tokenservice.dto.ValidationResult;
import com.example.tokenservice.lock.DistributedLock;
import com.example.tokenservice.store.TokenStore;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenService {
    
    private final JwtConfig jwtConfig;
    private final TokenStore tokenStore;
    private final DistributedLock distributedLock;
    private final SecretKey signingKey;
    
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String LOCK_KEY_REFRESH = "refresh:";
    private static final long LOCK_WAIT_TIME = 3;
    private static final long LOCK_LEASE_TIME = 10;
    
    @Autowired
    public TokenService(JwtConfig jwtConfig, TokenStore tokenStore, DistributedLock distributedLock) {
        this.jwtConfig = jwtConfig;
        this.tokenStore = tokenStore;
        this.distributedLock = distributedLock;
        this.signingKey = Keys.hmacShaKeyFor(
                jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }
    
    public TokenPair generateTokenPair(String userId, String username) {
        String traceId = TraceContext.getTraceId();
        
        log.info("[{}] Generating token pair for userId: {}, username: {}", 
                traceId, userId, maskUsername(username));
        
        Date now = new Date();
        
        long accessTokenTtl = jwtConfig.getAccessTokenExpiration();
        Date accessTokenExpiration = new Date(now.getTime() + accessTokenTtl);
        String accessToken = generateJwt(userId, username, TOKEN_TYPE_ACCESS, accessTokenExpiration);
        
        long refreshTokenTtl = jwtConfig.getRefreshTokenExpiration();
        Date refreshTokenExpiration = new Date(now.getTime() + refreshTokenTtl);
        String refreshToken = generateJwt(userId, username, TOKEN_TYPE_REFRESH, refreshTokenExpiration);
        
        tokenStore.saveAccessToken(userId, accessToken, accessTokenTtl);
        tokenStore.saveRefreshToken(userId, refreshToken, refreshTokenTtl);
        
        log.info("[{}] Token pair generated successfully for userId: {}", traceId, userId);
        
        return TokenPair.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(userId)
                .accessTokenExpiresAt(LocalDateTime.ofInstant(accessTokenExpiration.toInstant(), ZoneId.systemDefault()))
                .refreshTokenExpiresAt(LocalDateTime.ofInstant(refreshTokenExpiration.toInstant(), ZoneId.systemDefault()))
                .build();
    }
    
    public TokenPair refreshToken(String refreshToken) {
        String traceId = TraceContext.getTraceId();
        String tokenPreview = maskToken(refreshToken);
        
        log.info("[{}] Refreshing token: {}", traceId, tokenPreview);
        
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            log.warn("[{}] Refresh token is null or empty", traceId);
            throw new IllegalArgumentException("Refresh Token 不能为空");
        }
        
        String lockKey = LOCK_KEY_REFRESH + refreshToken;
        
        try {
            boolean locked = distributedLock.tryLock(
                    lockKey, 
                    LOCK_WAIT_TIME, 
                    LOCK_LEASE_TIME, 
                    TimeUnit.SECONDS
            );
            
            if (!locked) {
                log.warn("[{}] Failed to acquire lock for refresh token: {}", traceId, tokenPreview);
                throw new IllegalArgumentException("Refresh Token 正在处理中，请稍后重试");
            }
            
            try {
                if (tokenStore.isRefreshTokenUsed(refreshToken)) {
                    log.warn("[{}] Refresh token has been used: {}", traceId, tokenPreview);
                    throw new IllegalArgumentException("Refresh Token 已使用，请重新登录");
                }
                
                Claims claims;
                try {
                    claims = parseJwt(refreshToken);
                } catch (ExpiredJwtException e) {
                    log.warn("[{}] Refresh token expired: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
                    throw new IllegalArgumentException("Refresh Token 已过期");
                } catch (SignatureException e) {
                    log.warn("[{}] Refresh token signature invalid: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
                    throw new IllegalArgumentException("Refresh Token 签名无效");
                } catch (MalformedJwtException e) {
                    log.warn("[{}] Refresh token malformed: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
                    throw new IllegalArgumentException("Refresh Token 格式无效");
                }
                
                String tokenType = claims.get("type", String.class);
                if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
                    log.warn("[{}] Invalid token type for refresh: {}", traceId, tokenType);
                    throw new IllegalArgumentException("无效的 Token 类型");
                }
                
                String userId = claims.getSubject();
                String username = claims.get("username", String.class);
                
                String tokenTenantId = claims.get("tenantId", String.class);
                String currentTenantId = TenantContext.getTenantId();
                if (tokenTenantId != null && !tokenTenantId.equals(currentTenantId)) {
                    log.warn("[{}] Refresh token tenant mismatch: token={}, current={}", traceId, tokenTenantId, currentTenantId);
                    throw new IllegalArgumentException("租户不匹配");
                }
                
                if (!tokenStore.validateRefreshToken(userId, refreshToken)) {
                    log.warn("[{}] Refresh token validation failed for userId: {}", traceId, userId);
                    throw new IllegalArgumentException("Refresh Token 无效或已过期");
                }
                
                Optional<String> oldAccessTokenOpt = tokenStore.getAccessToken(userId);
                if (oldAccessTokenOpt.isPresent()) {
                    String oldAccessToken = oldAccessTokenOpt.get();
                    long remainingTtl = getRemainingTtl(claims);
                    if (remainingTtl > 0) {
                        tokenStore.addToBlacklist(oldAccessToken, remainingTtl);
                        log.info("[{}] Added old access token to blacklist for userId: {}", traceId, userId);
                    }
                }
                
                tokenStore.removeRefreshToken(userId);
                long refreshTokenRemainingTtl = getRemainingTtl(claims);
                if (refreshTokenRemainingTtl > 0) {
                    tokenStore.addToBlacklist(refreshToken, refreshTokenRemainingTtl);
                    log.info("[{}] Added old refresh token to blacklist for userId: {}", traceId, userId);
                }
                
                TokenPair newTokenPair = generateTokenPair(userId, username);
                
                tokenStore.markRefreshTokenUsed(
                        refreshToken, 
                        newTokenPair.getRefreshToken(), 
                        userId, 
                        jwtConfig.getRefreshTokenExpiration()
                );
                
                log.info("[{}] Token refreshed successfully for userId: {}", traceId, userId);
                
                return newTokenPair;
                
            } finally {
                distributedLock.unlock(lockKey);
            }
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] Failed to refresh token: {}, cause: {}", traceId, tokenPreview, e.getMessage(), e);
            throw new IllegalArgumentException("Token 刷新失败: " + e.getMessage());
        }
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
            if (tokenStore.isBlacklisted(token)) {
                log.warn("[{}] Token is in blacklist: {}", traceId, tokenPreview);
                return ValidationResult.builder()
                        .valid(false)
                        .message("Token 已作废")
                        .build();
            }
            
            Claims claims = parseJwt(token);
            
            String tokenType = claims.get("type", String.class);
            if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
                log.warn("[{}] Invalid token type for validation: {}", traceId, tokenType);
                return ValidationResult.builder()
                        .valid(false)
                        .message("无效的 Token 类型")
                        .build();
            }
            
            String userId = claims.getSubject();
            
            String tokenTenantId = claims.get("tenantId", String.class);
            String currentTenantId = TenantContext.getTenantId();
            if (tokenTenantId != null && !tokenTenantId.equals(currentTenantId)) {
                log.warn("[{}] Token tenant mismatch: token={}, current={}", traceId, tokenTenantId, currentTenantId);
                return ValidationResult.builder()
                        .valid(false)
                        .message("租户不匹配")
                        .build();
            }
            
            if (!tokenStore.validateAccessToken(userId, token)) {
                log.warn("[{}] Access token validation failed for userId: {}", traceId, userId);
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
            Claims claims = parseJwt(token);
            
            String userId = claims.getSubject();
            Date expiration = claims.getExpiration();
            long ttl = expiration.getTime() - System.currentTimeMillis();
            
            if (ttl > 0) {
                tokenStore.addToBlacklist(token, ttl);
                tokenStore.removeAccessToken(userId);
                
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
    
    private String generateJwt(String userId, String username, String tokenType, Date expiration) {
        Date now = new Date();
        String jti = UUID.randomUUID().toString();
        String tenantId = TenantContext.getTenantId();
        
        return Jwts.builder()
                .setId(jti)
                .setSubject(userId)
                .claim("username", username)
                .claim("type", tokenType)
                .claim("tenantId", tenantId)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    private Claims parseJwt(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    private long getRemainingTtl(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
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
