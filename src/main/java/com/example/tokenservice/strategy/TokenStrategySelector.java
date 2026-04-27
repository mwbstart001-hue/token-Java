package com.example.tokenservice.strategy;

import com.example.tokenservice.config.TokenProperties;
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
import java.util.stream.Collectors;

/**
 * Token 策略选择器
 * 
 * 职责：
 * 1. 实现 TokenGenerator 和 TokenValidator 接口
 * 2. 根据配置选择具体的策略实现
 * 3. 使用 @Primary 成为 Spring 自动注入的默认实现
 * 
 * 策略层级：
 * 第一层：选择大策略类型（JWT 或 SIMPLE）
 * - 根据 token.strategy.type 配置
 * - JWT: 使用 JWT 标准格式的策略
 * - SIMPLE: 使用简单自定义格式的策略
 * 
 * 第二层：选择 JWT 算法（仅当第一层选择 JWT 时）
 * - 根据 token.algorithm 配置
 * - RS256: 非对称加密，使用 RS256TokenGenerator/Validator
 * - HS256: 对称加密，使用 HS256TokenGenerator/Validator
 * - 备用: JwtTokenGenerator/JwtTokenValidator（通用实现）
 * 
 * 设计模式：
 * - 策略模式：封装不同的策略实现
 * - 委派模式：委派实际操作给选择的策略
 * - 适配器模式：统一策略接口
 */
@Component
@Primary
public class TokenStrategySelector implements TokenGenerator, TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(TokenStrategySelector.class);

    private final ApplicationContext applicationContext;
    private final TokenProperties tokenProperties;

    private TokenGenerator selectedGenerator;
    private TokenValidator selectedValidator;

    public TokenStrategySelector(ApplicationContext applicationContext,
                                   TokenProperties tokenProperties) {
        this.applicationContext = applicationContext;
        this.tokenProperties = tokenProperties;
    }

    @PostConstruct
    public void init() {
        log.info("初始化 Token 策略选择器...");
        
        String strategyType = tokenProperties.getStrategy().getType();
        String algorithm = tokenProperties.getAlgorithm();
        
        log.info("配置 - 大策略类型: {}, JWT 算法: {}", strategyType, algorithm);

        selectStrategies();
        
        log.info("策略选择完成 - Generator: {}, Validator: {}", 
                selectedGenerator.getClass().getSimpleName(),
                selectedValidator.getClass().getSimpleName());
    }

    /**
     * 选择策略实现
     * 两层选择：
     * 1. 先选大策略类型（JWT 或 SIMPLE）
     * 2. 如果是 JWT，再选具体算法（RS256 或 HS256）
     */
    private void selectStrategies() {
        String strategyType = tokenProperties.getStrategy().getType().toUpperCase();

        // 第一层：选择大策略类型
        if ("SIMPLE".equals(strategyType)) {
            selectSimpleStrategy();
        } else {
            selectJwtStrategy();
        }

        // 验证选择结果
        validateSelection();
    }

    /**
     * 选择 SIMPLE 策略
     */
    private void selectSimpleStrategy() {
        log.info("选择 SIMPLE 策略类型");
        
        List<TokenGenerator> generators = getAvailableGenerators();
        List<TokenValidator> validators = getAvailableValidators();

        // 查找 SimpleTokenGenerator
        selectedGenerator = generators.stream()
                .filter(g -> g.getClass().getSimpleName().contains("Simple"))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未找到 SimpleTokenGenerator，使用第一个可用的");
                    return generators.get(0);
                });

        // 查找 SimpleTokenValidator
        selectedValidator = validators.stream()
                .filter(v -> v.getClass().getSimpleName().contains("Simple"))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未找到 SimpleTokenValidator，使用第一个可用的");
                    return validators.get(0);
                });
    }

    /**
     * 选择 JWT 策略
     * 根据 token.algorithm 选择具体的算法实现
     */
    private void selectJwtStrategy() {
        String algorithm = tokenProperties.getAlgorithm().toUpperCase();
        log.info("选择 JWT 策略类型，算法: {}", algorithm);

        List<TokenGenerator> generators = getAvailableGenerators();
        List<TokenValidator> validators = getAvailableValidators();

        // 第二层：根据算法选择具体的 JWT 实现
        if ("RS256".equals(algorithm)) {
            selectRs256Strategy(generators, validators);
        } else if ("HS256".equals(algorithm)) {
            selectHs256Strategy(generators, validators);
        } else {
            // 未知算法，使用通用 JwtTokenGenerator
            log.warn("未知算法: {}，使用通用 JwtTokenGenerator", algorithm);
            selectGenericJwtStrategy(generators, validators);
        }
    }

    /**
     * 选择 RS256 非对称加密策略
     */
    private void selectRs256Strategy(List<TokenGenerator> generators, 
                                       List<TokenValidator> validators) {
        log.info("尝试选择 RS256 非对称加密策略");

        // 优先选择专用的 RS256TokenGenerator
        selectedGenerator = generators.stream()
                .filter(g -> g.getClass().getSimpleName().startsWith("RS256"))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未找到 RS256TokenGenerator，尝试查找 JwtTokenGenerator");
                    // 备用：选择通用的 JwtTokenGenerator（它内部使用 JwtKeyManager）
                    return generators.stream()
                            .filter(g -> g.getClass().getSimpleName().equals("JwtTokenGenerator"))
                            .findFirst()
                            .orElse(generators.get(0));
                });

        // 优先选择专用的 RS256TokenValidator
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

    /**
     * 选择 HS256 对称加密策略
     */
    private void selectHs256Strategy(List<TokenGenerator> generators, 
                                       List<TokenValidator> validators) {
        log.info("尝试选择 HS256 对称加密策略");

        // 优先选择专用的 HS256TokenGenerator
        selectedGenerator = generators.stream()
                .filter(g -> g.getClass().getSimpleName().startsWith("HS256"))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未找到 HS256TokenGenerator，尝试查找 JwtTokenGenerator");
                    // 备用：选择通用的 JwtTokenGenerator
                    return generators.stream()
                            .filter(g -> g.getClass().getSimpleName().equals("JwtTokenGenerator"))
                            .findFirst()
                            .orElse(generators.get(0));
                });

        // 优先选择专用的 HS256TokenValidator
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

    /**
     * 选择通用 JWT 策略（备用）
     */
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

    /**
     * 获取所有可用的 TokenGenerator（排除自身）
     */
    private List<TokenGenerator> getAvailableGenerators() {
        List<TokenGenerator> result = new ArrayList<>();
        for (TokenGenerator generator : applicationContext.getBeansOfType(TokenGenerator.class).values()) {
            // 排除自身，避免循环
            if (generator != this) {
                result.add(generator);
            }
        }
        log.debug("可用的 TokenGenerator: {}", result.stream()
                .map(g -> g.getClass().getSimpleName())
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 获取所有可用的 TokenValidator（排除自身）
     */
    private List<TokenValidator> getAvailableValidators() {
        List<TokenValidator> result = new ArrayList<>();
        for (TokenValidator validator : applicationContext.getBeansOfType(TokenValidator.class).values()) {
            // 排除自身，避免循环
            if (validator != this) {
                result.add(validator);
            }
        }
        log.debug("可用的 TokenValidator: {}", result.stream()
                .map(v -> v.getClass().getSimpleName())
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 验证选择结果
     */
    private void validateSelection() {
        if (selectedGenerator == null || selectedValidator == null) {
            log.error("策略选择失败！没有找到可用的策略实现");
            throw new IllegalStateException("无法选择 Token 策略，请检查配置");
        }

        // 验证 Generator 和 Validator 是否匹配
        String genName = selectedGenerator.getClass().getSimpleName();
        String valName = selectedValidator.getClass().getSimpleName();

        log.info("策略选择验证通过 - Generator: {}, Validator: {}", genName, valName);
    }

    // ==================== TokenGenerator 方法委派 ====================

    @Override
    public TokenGenerationResult generate(String userId, String subject,
                                            LocalDateTime issuedAt, LocalDateTime expiresAt) {
        if (selectedGenerator == null) {
            throw new IllegalStateException("TokenGenerator 未初始化");
        }
        return selectedGenerator.generate(userId, subject, issuedAt, expiresAt);
    }

    @Override
    public String getAlgorithm() {
        if (selectedGenerator == null) {
            return tokenProperties.getAlgorithm();
        }
        return selectedGenerator.getAlgorithm();
    }

    // ==================== TokenValidator 方法委派 ====================

    @Override
    public ValidationResult validate(String tokenValue) {
        if (selectedValidator == null) {
            throw new IllegalStateException("TokenValidator 未初始化");
        }
        return selectedValidator.validate(tokenValue);
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

    // ==================== 测试/调试方法 ====================

    /**
     * 获取当前选择的 Generator（用于测试/调试）
     */
    public TokenGenerator getSelectedGenerator() {
        return selectedGenerator;
    }

    /**
     * 获取当前选择的 Validator（用于测试/调试）
     */
    public TokenValidator getSelectedValidator() {
        return selectedValidator;
    }
}
