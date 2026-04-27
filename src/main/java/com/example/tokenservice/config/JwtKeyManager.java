package com.example.tokenservice.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT密钥管理服务
 * 支持 RS256 非对称加密算法和 HS256 对称加密算法
 * 提供密钥管理、Token 签名和验证解析能力
 * 
 * 职责：
 * 1. 密钥管理（RSA 密钥对或 HS256 密钥）
 * 2. 提供签名密钥（用于生成 Token）
 * 3. 提供验证密钥（用于解析 Token）
 * 4. JWT 解析能力（下沉自 TokenService）
 */
@Component
public class JwtKeyManager {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyManager.class);

    private final TokenProperties tokenProperties;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private volatile SecretKey secretKey;
    private SignatureAlgorithm algorithm;

    public JwtKeyManager(TokenProperties tokenProperties) {
        this.tokenProperties = tokenProperties;
    }

    @PostConstruct
    public void init() {
        log.info("初始化JWT密钥管理器...");

        String algo = tokenProperties.getAlgorithm();
        if ("RS256".equalsIgnoreCase(algo)) {
            this.algorithm = SignatureAlgorithm.RS256;
            initRsaKeys();
        } else if ("HS256".equalsIgnoreCase(algo)) {
            this.algorithm = SignatureAlgorithm.HS256;
            log.warn("使用HS256对称加密算法，不建议在生产环境使用");
            initHs256Secret();
        } else {
            throw new IllegalArgumentException("不支持的算法: " + algo + "，请使用 RS256 或 HS256");
        }

        log.info("JWT密钥管理器初始化完成，使用算法: {}", algorithm.getJcaName());
    }

    /**
     * 初始化 HS256 密钥
     * 使用双重检查锁定模式保证线程安全
     */
    private void initHs256Secret() {
        String secret = tokenProperties.getSecret();
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("HS256算法需要配置 token.secret");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("HS256密钥初始化完成");
    }

    /**
     * 初始化 RSA 密钥对
     * 优先使用配置中的密钥，如果没有配置则自动生成
     */
    private void initRsaKeys() {
        String configuredPrivateKey = tokenProperties.getPrivateKey();
        String configuredPublicKey = tokenProperties.getPublicKey();

        if (configuredPrivateKey != null && !configuredPrivateKey.isEmpty()
                && configuredPublicKey != null && !configuredPublicKey.isEmpty()) {
            log.info("从配置加载RSA密钥对...");
            try {
                this.privateKey = parsePrivateKey(configuredPrivateKey);
                this.publicKey = parsePublicKey(configuredPublicKey);
                log.info("RSA密钥对从配置加载成功");
            } catch (Exception e) {
                log.error("从配置加载RSA密钥对失败，将自动生成临时密钥", e);
                generateKeyPair();
            }
        } else {
            log.warn("未配置RSA密钥对，将自动生成临时密钥对。请注意：应用重启后密钥会变化！");
            generateKeyPair();
        }
    }

    /**
     * 生成 RSA 密钥对（2048位）
     */
    private void generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();

            log.info("RSA密钥对生成成功（2048位）");

            if (log.isDebugEnabled()) {
                log.debug("生成的私钥 (Base64): {}", Base64.getEncoder().encodeToString(privateKey.getEncoded()));
                log.debug("生成的公钥 (Base64): {}", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("无法生成RSA密钥对", e);
        }
    }

    /**
     * 解析 PEM 格式的私钥
     * @param pemPrivateKey PEM 格式的私钥（可以包含 -----BEGIN/END----- 标记）
     */
    private PrivateKey parsePrivateKey(String pemPrivateKey) throws InvalidKeySpecException, NoSuchAlgorithmException {
        String privateKeyPEM = pemPrivateKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * 解析 PEM 格式的公钥
     * @param pemPublicKey PEM 格式的公钥（可以包含 -----BEGIN/END----- 标记）
     */
    private PublicKey parsePublicKey(String pemPublicKey) throws InvalidKeySpecException, NoSuchAlgorithmException {
        String publicKeyPEM = pemPublicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * 获取签名用的密钥
     * - RS256: 返回私钥
     * - HS256: 返回对称密钥
     */
    public Key getSigningKey() {
        if (isRsaAlgorithm()) {
            return privateKey;
        } else {
            return getSecretKey();
        }
    }

    /**
     * 获取验证用的密钥
     * - RS256: 返回公钥
     * - HS256: 返回对称密钥
     */
    public Key getVerificationKey() {
        if (isRsaAlgorithm()) {
            return publicKey;
        } else {
            return getSecretKey();
        }
    }

    /**
     * 获取 HS256 对称加密密钥
     * 使用双重检查锁定模式保证线程安全
     */
    public SecretKey getSecretKey() {
        if (secretKey == null) {
            synchronized (this) {
                if (secretKey == null) {
                    String secret = tokenProperties.getSecret();
                    if (secret == null || secret.isEmpty()) {
                        throw new IllegalStateException("HS256算法需要配置 token.secret");
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
     * 获取私钥（用于签名）
     */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    /**
     * 获取公钥（用于验证）
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * 获取签名算法
     */
    public SignatureAlgorithm getAlgorithm() {
        return algorithm;
    }

    /**
     * 检查是否使用 RSA 非对称加密
     */
    public boolean isRsaAlgorithm() {
        return algorithm == SignatureAlgorithm.RS256
                || algorithm == SignatureAlgorithm.RS384
                || algorithm == SignatureAlgorithm.RS512;
    }

    /**
     * 解析 JWT Token（静默模式，不抛出异常）
     * 用于从 Token 中提取 userId 等信息，即使 Token 已过期
     * 
     * @param tokenValue Token 字符串
     * @return Claims 对象，如果解析失败返回 null
     */
    public Claims parseClaimsQuietly(String tokenValue) {
        try {
            Jws<Claims> jws = parseClaims(tokenValue);
            return jws.getBody();
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期，但成功解析Claims");
            return e.getClaims();
        } catch (Exception e) {
            log.debug("解析Token Claims失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析并验证 JWT Token
     * 会验证签名和有效期
     * 
     * @param tokenValue Token 字符串
     * @return Jws<Claims> 对象
     * @throws ExpiredJwtException 当 Token 已过期时抛出
     * @throws JwtException 当 Token 无效时抛出
     */
    public Jws<Claims> parseClaims(String tokenValue) throws JwtException {
        JwtParserBuilder parserBuilder = Jwts.parserBuilder()
                .setSigningKey(getVerificationKey());
        return parserBuilder.build().parseClaimsJws(tokenValue);
    }

    /**
     * 从 Token 中提取 userId
     * 静默模式，不抛出异常
     * 
     * @param tokenValue Token 字符串
     * @return userId，如果提取失败返回 "unknown"
     */
    public String extractUserIdQuietly(String tokenValue) {
        Claims claims = parseClaimsQuietly(tokenValue);
        if (claims != null) {
            String userId = claims.get("userId", String.class);
            if (userId != null && !userId.isEmpty()) {
                return userId;
            }
            String subject = claims.getSubject();
            if (subject != null && !subject.isEmpty()) {
                return subject;
            }
        }
        log.debug("无法从Token中提取userId");
        return "unknown";
    }

    /**
     * 从 Token 中提取 JWT ID (jti)
     * 静默模式，不抛出异常
     * 
     * @param tokenValue Token 字符串
     * @return jwtId，如果提取失败返回 null
     */
    public String extractJwtIdQuietly(String tokenValue) {
        Claims claims = parseClaimsQuietly(tokenValue);
        if (claims != null) {
            return claims.getId();
        }
        return null;
    }

    /**
     * 用于测试/调试的方法：导出当前使用的密钥对
     * 仅在日志级别为 DEBUG 时输出
     */
    public void logCurrentKeys() {
        if (log.isDebugEnabled() && isRsaAlgorithm()) {
            log.debug("当前私钥 (Base64): {}", Base64.getEncoder().encodeToString(privateKey.getEncoded()));
            log.debug("当前公钥 (Base64): {}", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        }
    }
}
