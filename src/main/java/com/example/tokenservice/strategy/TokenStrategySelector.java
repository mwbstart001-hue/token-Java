package com.example.tokenservice.strategy;

import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.monitor.TokenPerformanceMonitor;
import com.example.tokenservice.revocation.TokenRevocationService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@Primary
public class TokenStrategySelector implements TokenGenerator, TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(TokenStrategySelector.class);

    private final ApplicationContext applicationContext;
    private final TokenStrategyFactory strategyFactory;
    private final TokenProperties tokenProperties;
    private final TokenRevocationService revocationService;
    private final TokenPerformanceMonitor performanceMonitor;

    private final ReadWriteLock strategyLock = new ReentrantReadWriteLock();

    private String currentStrategyType;
    private String currentAlgorithm;

    public TokenStrategySelector(ApplicationContext applicationContext,
                                   TokenStrategyFactory strategyFactory,
                                   TokenProperties tokenProperties,
                                   TokenRevocationService revocationService,
                                   TokenPerformanceMonitor performanceMonitor) {
        this.applicationContext = applicationContext;
        this.strategyFactory = strategyFactory;
        this.tokenProperties = tokenProperties;
        this.revocationService = revocationService;
        this.performanceMonitor = performanceMonitor;
    }

    @PostConstruct
    public void init() {
        log.info("初始化 Token 策略选择器（工厂模式 + @Lookup 原型 Bean）...");
        
        String strategyType = tokenProperties.getStrategy().getType();
        String algorithm = tokenProperties.getAlgorithm();
        
        log.info("配置 - 大策略类型: {}, JWT 算法: {}", strategyType, algorithm);

        selectStrategies(strategyType, algorithm);
        
        log.info("策略选择完成 - 类型: {}, 算法: {}", currentStrategyType, currentAlgorithm);
    }

    private void selectStrategies(String strategyType, String algorithm) {
        strategyLock.writeLock().lock();
        try {
            this.currentStrategyType = strategyType.toUpperCase();
            this.currentAlgorithm = (algorithm != null) ? algorithm.toUpperCase() : "RS256";

            validateStrategySelection();
            
            log.info("策略配置更新 - 类型: {}, 算法: {}", currentStrategyType, currentAlgorithm);
        } finally {
            strategyLock.writeLock().unlock();
        }
    }

    private void validateStrategySelection() {
        String generatorBeanName = getGeneratorBeanName();
        String validatorBeanName = getValidatorBeanName();

        if (!applicationContext.containsBean(generatorBeanName)) {
            log.error("策略 Bean 不存在: {}", generatorBeanName);
            throw new IllegalStateException("无法选择 Token 策略，Bean 不存在: " + generatorBeanName);
        }
        if (!applicationContext.containsBean(validatorBeanName)) {
            log.error("策略 Bean 不存在: {}", validatorBeanName);
            throw new IllegalStateException("无法选择 Token 策略，Bean 不存在: " + validatorBeanName);
        }

        log.debug("策略验证通过 - Generator: {}, Validator: {}", generatorBeanName, validatorBeanName);
    }

    private String getGeneratorBeanName() {
        return strategyFactory.getStrategyType(currentStrategyType, currentAlgorithm)
                .getGeneratorBeanName();
    }

    private String getValidatorBeanName() {
        return strategyFactory.getStrategyType(currentStrategyType, currentAlgorithm)
                .getValidatorBeanName();
    }

    public TokenGenerator getNewGenerator() {
        strategyLock.readLock().lock();
        try {
            log.debug("通过工厂获取新的原型 Generator 实例 - 类型: {}, 算法: {}", 
                    currentStrategyType, currentAlgorithm);
            return strategyFactory.getGenerator(currentStrategyType, currentAlgorithm);
        } finally {
            strategyLock.readLock().unlock();
        }
    }

    public TokenValidator getNewValidator() {
        strategyLock.readLock().lock();
        try {
            log.debug("通过工厂获取新的原型 Validator 实例 - 类型: {}, 算法: {}", 
                    currentStrategyType, currentAlgorithm);
            return strategyFactory.getValidator(currentStrategyType, currentAlgorithm);
        } finally {
            strategyLock.readLock().unlock();
        }
    }

    public boolean switchStrategy(String strategyType, String algorithm) {
        if (strategyType == null || strategyType.isEmpty()) {
            log.warn("策略类型不能为空");
            return false;
        }

        String upperType = strategyType.toUpperCase();
        String upperAlgo = (algorithm != null) ? algorithm.toUpperCase() : "RS256";

        if (!"JWT".equals(upperType) && !"SIMPLE".equals(upperType)) {
            log.warn("不支持的策略类型: {}", strategyType);
            return false;
        }

        log.info("运行时切换策略: type={}, algorithm={}", upperType, upperAlgo);

        try {
            selectStrategies(upperType, upperAlgo);
            log.info("策略切换成功 - 类型: {}, 算法: {}", currentStrategyType, currentAlgorithm);
            return true;
        } catch (Exception e) {
            log.error("策略切换失败: {}", e.getMessage(), e);
            return false;
        }
    }

    public String getCurrentStrategyType() {
        strategyLock.readLock().lock();
        try {
            return currentStrategyType;
        } finally {
            strategyLock.readLock().unlock();
        }
    }

    public String getCurrentAlgorithm() {
        strategyLock.readLock().lock();
        try {
            return currentAlgorithm;
        } finally {
            strategyLock.readLock().unlock();
        }
    }

    @Override
    public TokenGenerationResult generate(String userId, String subject,
                                            LocalDateTime issuedAt, LocalDateTime expiresAt) {
        long startTime = System.currentTimeMillis();
        
        TokenGenerator generator = getNewGenerator();
        String strategyName = generator.getClass().getSimpleName();
        
        try {
            TokenGenerationResult result = generator.generate(userId, subject, issuedAt, expiresAt);
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitor.recordGenerate(strategyName, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitor.recordGenerate(strategyName, duration, true);
            throw e;
        }
    }

    @Override
    public String getAlgorithm() {
        TokenGenerator generator = getNewGenerator();
        return generator.getAlgorithm();
    }

    @Override
    public ValidationResult validate(String tokenValue) {
        long startTime = System.currentTimeMillis();
        
        TokenValidator validator = getNewValidator();
        String strategyName = validator.getClass().getSimpleName();

        try {
            Claims claims = validator.parseQuietly(tokenValue);
            String jwtId = claims != null ? claims.getId() : null;

            if (revocationService.isRevoked(jwtId, tokenValue)) {
                long duration = System.currentTimeMillis() - startTime;
                performanceMonitor.recordValidate(strategyName, duration);
                log.debug("Token 已被吊销: jwtId={}", jwtId);
                return ValidationResult.invalid(ValidationStatus.INVALID, "Token 已被吊销");
            }

            ValidationResult result = validator.validate(tokenValue);
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitor.recordValidate(strategyName, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitor.recordValidate(strategyName, duration, true);
            throw e;
        }
    }

    @Override
    public Claims parseQuietly(String tokenValue) {
        TokenValidator validator = getNewValidator();
        return validator.parseQuietly(tokenValue);
    }

    @Override
    public String extractUserId(String tokenValue) {
        TokenValidator validator = getNewValidator();
        return validator.extractUserId(tokenValue);
    }

    @Override
    public String extractJwtId(String tokenValue) {
        TokenValidator validator = getNewValidator();
        return validator.extractJwtId(tokenValue);
    }

    public TokenGenerator getSelectedGenerator() {
        return getNewGenerator();
    }

    public TokenValidator getSelectedValidator() {
        return getNewValidator();
    }

    public TokenStrategyFactory getStrategyFactory() {
        return strategyFactory;
    }
}
