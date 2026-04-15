package com.example.tokenservice.service;

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
        
        return TokenResponse.builder()
                .token(token)
                .userId(userId)
                .issuedAt(LocalDateTime.ofInstant(now.toInstant(), ZoneId.systemDefault()))
                .expiresAt(LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()))
                .build();
    }
    
    public ValidationResult validateToken(String token) {
        try {
            if (tokenBlacklist.containsKey(token)) {
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
        } catch (SignatureException e) {
            log.warn("Token 签名验证失败: {}", e.getMessage());
            return ValidationResult.builder()
                    .valid(false)
                    .message("Token 签名验证失败")
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Token 为空或无效: {}", e.getMessage());
            return ValidationResult.builder()
                    .valid(false)
                    .message("Token 为空或无效")
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
        String token = userTokenMap.get(userId);
        
        if (token != null) {
            Long expiryTime = tokenExpiryMap.get(token);
            if (expiryTime != null && expiryTime > System.currentTimeMillis()) {
                tokenBlacklist.put(token, expiryTime);
            }
            userTokenMap.remove(userId);
            tokenExpiryMap.remove(token);
            
            log.info("用户 {} 的 Token 已作废", userId);
            return true;
        }
        
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
        
        tokenBlacklist.entrySet().removeIf(entry -> entry.getValue() <= now);
        
        tokenExpiryMap.entrySet().removeIf(entry -> {
            if (entry.getValue() <= now) {
                userTokenMap.values().removeIf(v -> v.equals(entry.getKey()));
                return true;
            }
            return false;
        });
        
        if (log.isDebugEnabled()) {
            log.debug("已清理过期 Token, 当前有效用户数: {}, 黑名单数: {}", 
                    userTokenMap.size(), tokenBlacklist.size());
        }
    }
}
