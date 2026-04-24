package com.example.tokenservice.config;

import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
 * 支持RS256非对称加密算法
 * 如果配置中没有提供密钥对，将自动生成临时密钥对用于开发/测试
 */
@Component
public class JwtKeyManager {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyManager.class);

    private final TokenProperties tokenProperties;

    private PrivateKey privateKey;
    private PublicKey publicKey;
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
        } else {
            throw new IllegalArgumentException("不支持的算法: " + algo + "，请使用 RS256 或 HS256");
        }

        log.info("JWT密钥管理器初始化完成，使用算法: {}", algorithm.getJcaName());
    }

    /**
     * 初始化RSA密钥对
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
     * 生成RSA密钥对（2048位）
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
     * 解析PEM格式的私钥
     * @param pemPrivateKey PEM格式的私钥（可以包含-----BEGIN/END-----标记）
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
     * 解析PEM格式的公钥
     * @param pemPublicKey PEM格式的公钥（可以包含-----BEGIN/END-----标记）
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
     * 检查是否使用RSA非对称加密
     */
    public boolean isRsaAlgorithm() {
        return algorithm == SignatureAlgorithm.RS256
                || algorithm == SignatureAlgorithm.RS384
                || algorithm == SignatureAlgorithm.RS512;
    }

    /**
     * 用于测试/调试的方法：导出当前使用的密钥对
     * 仅在日志级别为DEBUG时输出
     */
    public void logCurrentKeys() {
        if (log.isDebugEnabled() && isRsaAlgorithm()) {
            log.debug("当前私钥 (Base64): {}", Base64.getEncoder().encodeToString(privateKey.getEncoded()));
            log.debug("当前公钥 (Base64): {}", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        }
    }
}
