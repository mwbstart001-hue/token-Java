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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Component
@Primary
public class TokenStrategySelector implements TokenGenerator, TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(TokenStrategySelector.class);

    private final ApplicationContext applicationContext;
    private final TokenProperties tokenProperties;
    private final TokenRevocationService revocationService;
    private final TokenPerformanceMonitor performanceMonitor;

    private TokenGenerator selectedGenerator;
    private TokenValidator selectedValidator;
    private final ReadWriteLock strategyLock = new ReentrantReadWriteLock();

    private String currentStrategyType;
    private String currentAlgorithm;

    public TokenStrategySelector(ApplicationContext applicationContext,
                                   TokenProperties tokenProperties,
                                   TokenRevocationService revocationService,
                                   TokenPerformanceMonitor performanceMonitor) {
        this.applicationContext = applicationContext;
        this.tokenProperties = tokenProperties;
        this.revocationService = revocationService;
        this.performanceMonitor = performanceMonitor;
    }

    @PostConstruct
    public void init() {
        log.info("初始化 Token 策略选择器...");
        
        String strategyType = tokenProperties.getStrategy().getType();
        String algorithm = tokenProperties.getAlgorithm();
        
        log.info("配置 - 大策略类型: {}, JWT 算法: {}", strategyType, algorithm);

        selectStrategies(strategyType, algorithm);
        
        log.info("策略选择完成 - Generator: {}, Validator: {}", 
                selectedGenerator.getClass().getSimpleName(),
                selectedValidator.getClass().getSimpleName());
    }

    private void selectStrategies(String strategyType, String algorithm) {
        strategyLock.writeLock().lock();
        try {
            this.currentStrategyType = strategyType.toUpperCase();
            this.currentAlgorithm = algorithm.toUpperCase();

            if ("SIMPLE".equals(this.currentStrategyType)) {
                selectSimpleStrategy();
            } else {
                selectJwtStrategy(this.currentAlgorithm);
            }

            validateSelection();
        } finally {
            strategyLock.writeLock().unlock();
        }
    }

    private void selectSimpleStrategy() {
        log.info("选择 SIMPLE 策略类型");
        
        List<TokenGenerator> generators = getAvailableGenerators();
        List<TokenValidator> validators = getAvailableValidators();

        selectedGenerator = generators.stream()
                .filter(g -> g.getClass().getSimpleName().contains("Simple"))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未找到 SimpleTokenGenerator，使用第一个可用的");
                    return generators.get(0);
                });

        selectedValidator = validators.stream()
                .filter(v -> v.getClass().getSimpleName().contains("Simple"))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未找到 SimpleTokenValidator，使用第一个可用的");
                    return validators.get(0);
                });
    }

    private void selectJwtStrategy(String algorithm) {
        log.info("选择 JWT 策略类型，算法: {}", algorithm);

        List<TokenGenerator> generators = getAvailableGenerators();
        List<TokenValidator> validators = getAvailableValidators();

        if ("RS256".equals(algorithm)) {
            selectRs256Strategy(generators, validators);
        } else if ("HS256".equals(algorithm)) {
            selectHs256Strategy(generators, validators);
        } else {
            log.warn("未知算法: {}，使用通用 JwtTokenGenerator", algorithm);
            selectGenericJwtStrategy(generators, validators);
        }
    }

    private void selectRs256Strategy(List<TokenGenerator> generators, 
                                       List<TokenValidator> validators) {
        log.info("尝试选择 RS256 非对称加密策略");

        selectedGenerator = generators.stream()
                .filter(g -> g.getClass().getSimpleName().startsWith("RS256"))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未找到 RS256TokenGenerator，尝试查找 JwtTokenGenerator");
                    return generators.stream()
                            .filter(g -> g.getClass().getSimpleName().equals("JwtTokenGenerator"))
                            .findFirst()
                            .orElse(generators.get(0));
                });

        selectedValidator = validators.stream()
                .filter(v -> v.getClass().getSimpleName().startsWith("RS256"))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未找到 RS256TokenValidator，尝试查找 JwtTokenValidator");
                    return validators.stream()
                            .filter(v -> v.getClass().getSimpleName().equals("JwtTokenValidator"))
                            .findFirst()
                            .orElse(validators.get(0));
                });
    }

    private void selectHs256Strategy(List<TokenGenerator> generators, 
                                       List<TokenValidator> validators) {
        log.info("尝试选择 HS256 对称加密策略");

        selectedGenerator = generators.stream()
                .filter(g -> g.getClass().getSimpleName().startsWith("HS256"))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未找到 HS256TokenGenerator，尝试查找 JwtTokenGenerator");
                    return generators.stream()
                            .filter(g -> g.getClass().getSimpleName().equals("JwtTokenGenerator"))
                            .findFirst()
                            .orElse(generators.get(0));
                });

        selectedValidator = validators.stream()
                .filter(v -> v.getClass().getSimpleName().startsWith("HS256"))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未找到 HS256TokenValidator，尝试查找 JwtTokenValidator");
                    return validators.stream()
                            .filter(v -> v.getClass().getSimpleName().equals("JwtTokenValidator"))
                            .findFirst()
                            .orElse(validators.get(0));
                });
    }

    private void selectGenericJwtStrategy(List<TokenGenerator> generators, 
                                            List<TokenValidator> validators) {
        log.info("选择通用 JWT 策略");

        selectedGenerator = generators.stream()
                .filter(g -> g.getClass().getSimpleName().equals("JwtTokenGenerator"))
                .findFirst()
                .orElse(generators.get(0));

        selectedValidator = validators.stream()
                .filter(v -> v.getClass().getSimpleName().equals("JwtTokenValidator"))
                .findFirst()
                .orElse(validators.get(0));
    }

    private List<TokenGenerator> getAvailableGenerators() {
        List<TokenGenerator> result = new ArrayList<>();
        for (TokenGenerator generator : applicationContext.getBeansOfType(TokenGenerator.class).values()) {
            if (generator != this) {
                result.add(generator);
            }
        }
        log.debug("可用的 TokenGenerator: {}", result.stream()
                .map(g -> g.getClass().getSimpleName())
                .collect(Collectors.toList()));
        return result;
    }

    private List<TokenValidator> getAvailableValidators() {
        List<TokenValidator> result = new ArrayList<>();
        for (TokenValidator validator : applicationContext.getBeansOfType(TokenValidator.class).values()) {
            if (validator != this) {
                result.add(validator);
            }
        }
        log.debug("可用的 TokenValidator: {}", result.stream()
                .map(v -> v.getClass().getSimpleName())
                .collect(Collectors.toList()));
        return result;
    }

    private void validateSelection() {
        if (selectedGenerator == null || selectedValidator == null) {
            log.error("策略选择失败！没有找到可用的策略实现");
            throw new IllegalStateException("无法选择 Token 策略，请检查配置");
        }

        String genName = selectedGenerator.getClass().getSimpleName();
        String valName = selectedValidator.getClass().getSimpleName();

        log.info("策略选择验证通过 - Generator: {}, Validator: {}", genName, valName);
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
            log.info("策略切换成功 - Generator: {}, Validator: {}", 
                    selectedGenerator.getClass().getSimpleName(),
                    selectedValidator.getClass().getSimpleName());
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
        
        if (selectedGenerator == null) {
            throw new IllegalStateException("TokenGenerator 未初始化");
        }
        
        try {
            TokenGenerationResult result = selectedGenerator.generate(userId, subject, issuedAt, expiresAt);
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitor.recordGenerate(selectedGenerator.getClass().getSimpleName(), duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitor.recordGenerate(selectedGenerator.getClass().getSimpleName(), duration, true);
            throw e;
        }
    }

    @Override
    public String getAlgorithm() {
        if (selectedGenerator == null) {
            return tokenProperties.getAlgorithm();
        }
        return selectedGenerator.getAlgorithm();
    }

    @Override
    public ValidationResult validate(String tokenValue) {
        long startTime = System.currentTimeMillis();
        
        if (selectedValidator == null) {
            throw new IllegalStateException("TokenValidator 未初始化");
        }

        try {
            Claims claims = selectedValidator.parseQuietly(tokenValue);
            String jwtId = claims != null ? claims.getId() : null;

            if (revocationService.isRevoked(jwtId, tokenValue)) {
                long duration = System.currentTimeMillis() - startTime;
                performanceMonitor.recordValidate(selectedValidator.getClass().getSimpleName(), duration);
                log.debug("Token 已被吊销: jwtId={}", jwtId);
                return ValidationResult.invalid(ValidationStatus.INVALID, "Token 已被吊销");
            }

            ValidationResult result = selectedValidator.validate(tokenValue);
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitor.recordValidate(selectedValidator.getClass().getSimpleName(), duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceMonitor.recordValidate(selectedValidator.getClass().getSimpleName(), duration, true);
            throw e;
        }
    }

    @Override
    public Claims parseQuietly(String tokenValue) {
        if (selectedValidator == null) {
            throw new IllegalStateException("TokenValidator 未初始化");
        }
        return selectedValidator.parseQuietly(tokenValue);
    }

    @Override
    public String extractUserId(String tokenValue) {
        if (selectedValidator == null) {
            throw new IllegalStateException("TokenValidator 未初始化");
        }
        return selectedValidator.extractUserId(tokenValue);
    }

    @Override
    public String extractJwtId(String tokenValue) {
        if (selectedValidator == null) {
            throw new IllegalStateException("TokenValidator 未初始化");
        }
        return selectedValidator.extractJwtId(tokenValue);
    }

    public TokenGenerator getSelectedGenerator() {
        return selectedGenerator;
    }

    public TokenValidator getSelectedValidator() {
        return selectedValidator;
    }
}
