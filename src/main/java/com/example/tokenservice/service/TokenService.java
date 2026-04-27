package com.example.tokenservice.service;

import com.example.tokenservice.config.JwtKeyManager;
import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.dto.TokenInfo;
import com.example.tokenservice.exception.ErrorCode;
import com.example.tokenservice.exception.TokenExpiredException;
import com.example.tokenservice.exception.TokenInvalidException;
import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Token核心服务类
 * 提供Token的生成、验证、查询、作废等核心功能
 * 支持HS256和RS256两种签名算法
 * 所有操作均保证线程安全
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final TokenStore tokenStore;
    private final TokenProperties tokenProperties;
    private final JwtKeyManager jwtKeyManager;

    private volatile SecretKey secretKey;

    public TokenService(TokenStore tokenStore,
                        TokenProperties tokenProperties,
                        JwtKeyManager jwtKeyManager) {
        this.tokenStore = tokenStore;
        this.tokenProperties = tokenProperties;
        this.jwtKeyManager = jwtKeyManager;
    }

    /**
     * 获取HS256对称加密密钥
     * 使用双重检查锁定模式保证线程安全
     */
    private SecretKey getSecretKey() {
        if (secretKey == null) {
            synchronized (this) {
                if (secretKey == null) {
                    String secret = tokenProperties.getSecret();
                    if (secret == null || secret.isEmpty()) {
                        throw new IllegalStateException("HS256算法需要配置token.secret");
                    }
                    secretKey = Keys.hmacShaKeyFor(
                            secret.getBytes(StandardCharsets.UTF_8)
                    );
                }
            }
        }
        return secretKey;
    }

    /**
     * 生成Token
     * 
     * @param userId 用户唯一标识
     * @param subject Token主题/用途（可选）
     * @param expireSeconds 过期时间（秒），null则使用默认值
     * @return 生成的JWT Token字符串
     */
    public String generateToken(String userId, String subject, Long expireSeconds) {
        log.info("开始生成Token - userId: {}, subject: {}, expireSeconds: {}", userId, subject, expireSeconds);

        long actualExpireSeconds = calculateExpireSeconds(expireSeconds);
        log.debug("Token过期时间: {}秒", actualExpireSeconds);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(actualExpireSeconds);

        String jwtId = UUID.randomUUID().toString();
        log.debug("Token JWT ID: {}", jwtId);

        String tokenValue = buildJwt(jwtId, userId, subject, now, expiresAt);

        Token token = createTokenEntity(tokenValue, userId, subject, now, expiresAt);
        tokenStore.save(token);

        log.info("Token生成成功 - userId: {}, jwtId: {}, expiresAt: {}", userId, jwtId, expiresAt);
        return tokenValue;
    }

    /**
     * 计算实际的过期时间
     * 限制最大过期时间，防止生成永不过期的token
     */
    private long calculateExpireSeconds(Long expireSeconds) {
        if (expireSeconds == null) {
            return tokenProperties.getDefaultExpireSeconds();
        }
        if (expireSeconds <= 0) {
            log.warn("过期时间为负数或零，使用默认值: {}秒", tokenProperties.getDefaultExpireSeconds());
            return tokenProperties.getDefaultExpireSeconds();
        }
        long maxExpireSeconds = tokenProperties.getMaxExpireSeconds();
        if (expireSeconds > maxExpireSeconds) {
            log.warn("过期时间 {} 超过最大值 {}，将被限制为最大值", expireSeconds, maxExpireSeconds);
            return maxExpireSeconds;
        }
        return expireSeconds;
    }

    /**
     * 构建JWT Token
     * 根据配置选择使用HS256或RS256算法
     */
    private String buildJwt(String jwtId, String userId, String subject,
                            LocalDateTime now, LocalDateTime expiresAt) {
        JwtBuilder builder = Jwts.builder()
                .setId(jwtId)
                .setSubject(subject != null ? subject : userId)
                .setIssuer("token-service")
                .setIssuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .setExpiration(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()))
                .claim("userId", userId);

        if (jwtKeyManager.isRsaAlgorithm()) {
            log.debug("使用RS256算法签名");
            builder.signWith(jwtKeyManager.getPrivateKey(), jwtKeyManager.getAlgorithm());
        } else {
            log.debug("使用HS256算法签名");
            builder.signWith(getSecretKey(), SignatureAlgorithm.HS256);
        }

        return builder.compact();
    }

    /**
     * 创建Token实体对象
     */
    private Token createTokenEntity(String tokenValue, String userId, String subject,
                                      LocalDateTime now, LocalDateTime expiresAt) {
        Token token = new Token();
        token.setTokenValue(tokenValue);
        token.setUserId(userId);
        token.setSubject(subject);
        token.setIssuedAt(now);
        token.setExpiresAt(expiresAt);
        token.setStatus(TokenStatus.ACTIVE);
        return token;
    }

    /**
     * 验证Token有效性
     * 包含以下检查：
     * 1. Token是否存在于存储中
     * 2. Token状态是否为ACTIVE
     * 3. Token是否已过期
     * 4. JWT签名是否有效
     * 
     * @param tokenValue 待验证的Token
     * @return true表示有效，false表示无效
     */
    public boolean validateToken(String tokenValue) {
        log.debug("开始验证Token");

        Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);

        if (!tokenOpt.isPresent()) {
            log.warn("Token不存在");
            return false;
        }

        Token token = tokenOpt.get();

        if (!token.isValid()) {
            log.warn("Token状态无效 - status: {}, expired: {}", token.getStatus(), token.isExpired());
            return false;
        }

        try {
            parseAndVerifyJwt(tokenValue);
            log.debug("Token验证成功 - userId: {}", token.getUserId());
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期 - userId: {}", token.getUserId());
            tokenStore.updateStatus(tokenValue, TokenStatus.EXPIRED);
            return false;
        } catch (SignatureException e) {
            log.warn("Token签名无效");
            tokenStore.updateStatus(tokenValue, TokenStatus.INVALIDATED);
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Token格式错误");
            tokenStore.updateStatus(tokenValue, TokenStatus.INVALIDATED);
            return false;
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的Token类型");
            tokenStore.updateStatus(tokenValue, TokenStatus.INVALIDATED);
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token验证失败: {}", e.getMessage());
            tokenStore.updateStatus(tokenValue, TokenStatus.INVALIDATED);
            return false;
        }
    }

    /**
     * 解析并验证JWT签名
     * 根据配置选择使用公钥（RS256）或密钥（HS256）
     * @throws TokenExpiredException 当Token已过期时抛出
     * @throws TokenInvalidException 当Token无效时抛出
     */
    private Jws<Claims> parseAndVerifyJwt(String tokenValue) {
        JwtParserBuilder parserBuilder = Jwts.parserBuilder();

        if (jwtKeyManager.isRsaAlgorithm()) {
            parserBuilder.setSigningKey(jwtKeyManager.getPublicKey());
        } else {
            parserBuilder.setSigningKey(getSecretKey());
        }

        return parserBuilder.build().parseClaimsJws(tokenValue);
    }

    /**
     * 解析JWT获取Claims（不抛出异常，用于获取已过期Token的信息）
     * 
     * @param tokenValue Token字符串
     * @return Claims对象，如果解析失败返回null
     */
    public Claims parseClaimsQuietly(String tokenValue) {
        try {
            JwtParserBuilder parserBuilder = Jwts.parserBuilder();

            if (jwtKeyManager.isRsaAlgorithm()) {
                parserBuilder.setSigningKey(jwtKeyManager.getPublicKey());
            } else {
                parserBuilder.setSigningKey(getSecretKey());
            }

            JwtParser parser = parserBuilder.build();

            return parser.parseClaimsJws(tokenValue).getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        } catch (Exception e) {
            log.debug("解析Token Claims失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 续签Token
     * 基于现有有效Token生成新的Token，可选择作废原Token
     * 
     * @param oldTokenValue 原Token值
     * @param newExpireSeconds 新的过期时间（秒），null则使用默认值
     * @param invalidateOldToken 是否作废原Token
     * @return 新生成的Token值，如果原Token无效则返回null
     */
    public String renewToken(String oldTokenValue, Long newExpireSeconds, boolean invalidateOldToken) {
        log.info("开始续签Token");

        Optional<Token> oldTokenOpt = tokenStore.findByTokenValue(oldTokenValue);

        if (!oldTokenOpt.isPresent()) {
            log.warn("续签Token失败：原Token不存在");
            return null;
        }

        Token oldToken = oldTokenOpt.get();

        if (!oldToken.isValid()) {
            log.warn("续签Token失败：原Token状态无效 - status: {}, expired: {}", 
                    oldToken.getStatus(), oldToken.isExpired());
            return null;
        }

        try {
            Jws<Claims> jws = parseAndVerifyJwt(oldTokenValue);
            Claims claims = jws.getBody();

            String userId = oldToken.getUserId();
            String subject = oldToken.getSubject();

            String newJwtId = UUID.randomUUID().toString();
            log.debug("新Token JWT ID: {}", newJwtId);

            long actualExpireSeconds = calculateExpireSeconds(newExpireSeconds);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plusSeconds(actualExpireSeconds);

            String newTokenValue = buildJwt(newJwtId, userId, subject, now, expiresAt);

            Token newToken = createTokenEntity(newTokenValue, userId, subject, now, expiresAt);
            tokenStore.save(newToken);

            if (invalidateOldToken) {
                tokenStore.updateStatus(oldTokenValue, TokenStatus.INVALIDATED);
                log.debug("已作废原Token");
            }

            log.info("Token续签成功 - userId: {}, newJwtId: {}, expiresAt: {}", userId, newJwtId, expiresAt);
            return newTokenValue;

        } catch (ExpiredJwtException e) {
            log.warn("续签Token失败：原Token已过期 - userId: {}", oldToken.getUserId());
            return null;
        } catch (SignatureException e) {
            log.warn("续签Token失败：原Token签名无效");
            return null;
        } catch (Exception e) {
            log.warn("续签Token失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取Token详细信息
     * 
     * @param tokenValue Token字符串
     * @return Token信息，如果不存在则返回Optional.empty()
     */
    public Optional<TokenInfo> getTokenInfo(String tokenValue) {
        log.debug("获取Token信息");

        Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);

        if (!tokenOpt.isPresent()) {
            log.warn("获取Token信息失败：Token不存在");
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

        log.debug("获取Token信息成功 - userId: {}, valid: {}", token.getUserId(), token.isValid());
        return Optional.of(info);
    }

    /**
     * 作废Token
     * 将Token状态从ACTIVE更改为INVALIDATED
     * 
     * @param tokenValue 待作废的Token
     * @return true表示作废成功，false表示Token不存在或已作废
     */
    public boolean invalidateToken(String tokenValue) {
        log.info("尝试作废Token");

        Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);

        if (!tokenOpt.isPresent()) {
            log.warn("作废Token失败：Token不存在");
            return false;
        }

        Token token = tokenOpt.get();

        if (token.getStatus() != TokenStatus.ACTIVE) {
            log.warn("作废Token失败：Token状态已为 {}", token.getStatus());
            return false;
        }

        tokenStore.updateStatus(tokenValue, TokenStatus.INVALIDATED);
        log.info("Token作废成功 - userId: {}", token.getUserId());

        return true;
    }

    /**
     * 清理过期Token
     * 从存储中删除所有已过期的Token记录
     * 用于定时任务或手动释放存储空间
     */
    public void clearExpiredTokens() {
        log.info("开始清理过期Token");
        tokenStore.clearExpired();
        log.info("过期Token清理完成");
    }
}
