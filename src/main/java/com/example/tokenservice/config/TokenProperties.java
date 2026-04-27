package com.example.tokenservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Token配置属性类
 * 用于从application.yml中读取token相关配置
 * 支持HS256和RS256两种签名算法
 */
@Component
@ConfigurationProperties(prefix = "token")
public class TokenProperties {

    /**
     * 签名算法：HS256（对称加密）或 RS256（非对称加密）
     */
    private String algorithm = "RS256";

    /**
     * HS256对称加密密钥（已弃用，保留用于向后兼容）
     */
    private String secret;

    /**
     * RS256私钥（PEM格式，用于签名）
     * 如果未配置，将自动生成临时密钥对
     */
    private String privateKey;

    /**
     * RS256公钥（PEM格式，用于验证）
     * 如果未配置，将自动生成临时密钥对
     */
    private String publicKey;

    /**
     * 默认过期时间（秒）
     */
    private long defaultExpireSeconds = 3600;

    /**
     * 最大过期时间（秒），防止生成永不过期的token
     */
    private long maxExpireSeconds = 86400L * 30;

    /**
     * 存储类型：memory 或 database
     */
    private String storageType = "memory";

    /**
     * API密钥配置，用于保护API端点
     */
    private ApiKey apiKey = new ApiKey();

    /**
     * 统计功能配置
     */
    private Statistics statistics = new Statistics();

    /**
     * 调度器配置
     */
    private Scheduler scheduler = new Scheduler();

    /**
     * Token生成策略配置
     */
    private TokenStrategy strategy = new TokenStrategy();

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public long getDefaultExpireSeconds() {
        return defaultExpireSeconds;
    }

    public void setDefaultExpireSeconds(long defaultExpireSeconds) {
        this.defaultExpireSeconds = defaultExpireSeconds;
    }

    public long getMaxExpireSeconds() {
        return maxExpireSeconds;
    }

    public void setMaxExpireSeconds(long maxExpireSeconds) {
        this.maxExpireSeconds = maxExpireSeconds;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKey apiKey) {
        this.apiKey = apiKey;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public TokenStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(TokenStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * API密钥配置类
     */
    public static class ApiKey {

        /**
         * 是否启用API密钥认证
         */
        private boolean enabled = true;

        /**
         * 头名称，默认为 X-API-Key
         */
        private String headerName = "X-API-Key";

        /**
         * 允许的API密钥列表
         */
        private String[] allowedKeys = {"default-api-key-for-test-only"};

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String[] getAllowedKeys() {
            return allowedKeys;
        }

        public void setAllowedKeys(String[] allowedKeys) {
            this.allowedKeys = allowedKeys;
        }
    }

    /**
     * 统计功能配置类
     */
    public static class Statistics {

        /**
         * 是否启用统计功能
         */
        private boolean enabled = true;

        /**
         * 是否记录无效Token操作
         */
        private boolean recordInvalidTokens = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRecordInvalidTokens() {
            return recordInvalidTokens;
        }

        public void setRecordInvalidTokens(boolean recordInvalidTokens) {
            this.recordInvalidTokens = recordInvalidTokens;
        }
    }

    /**
     * 调度器配置类
     */
    public static class Scheduler {

        /**
         * 异步执行核心线程数
         */
        private int corePoolSize = 4;

        /**
         * 异步执行最大线程数
         */
        private int maxPoolSize = 8;

        /**
         * 队列容量
         */
        private int queueCapacity = 100;

        /**
         * 是否启用异步执行
         */
        private boolean asyncEnabled = true;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public boolean isAsyncEnabled() {
            return asyncEnabled;
        }

        public void setAsyncEnabled(boolean asyncEnabled) {
            this.asyncEnabled = asyncEnabled;
        }
    }

    /**
     * Token生成策略配置类
     */
    public static class TokenStrategy {

        /**
         * 策略类型：JWT 或 SIMPLE
         */
        private String type = "JWT";

        /**
         * 策略优先级
         */
        private int priority = 1;

        /**
         * 简单策略的密钥前缀
         */
        private String simplePrefix = "TOKEN-";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public String getSimplePrefix() {
            return simplePrefix;
        }

        public void setSimplePrefix(String simplePrefix) {
            this.simplePrefix = simplePrefix;
        }
    }
}
