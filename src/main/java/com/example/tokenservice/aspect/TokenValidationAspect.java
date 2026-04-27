package com.example.tokenservice.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Token参数验证切面
 * 使用AOP统一处理TokenService方法的参数验证
 * 解耦业务逻辑与参数验证
 * 
 * 职责：
 * 1. 验证方法参数的合法性
 * 2. 提前拦截无效参数，避免进入业务逻辑
 * 3. 统一处理参数验证失败的情况
 */
@Aspect
@Component
public class TokenValidationAspect {

    private static final Logger log = LoggerFactory.getLogger(TokenValidationAspect.class);

    /**
     * 验证生成Token的参数
     * - userId 不能为空
     * - expireSeconds 应为正数（如果提供）
     */
    @Around("execution(* com.example.tokenservice.service.TokenService.generateToken(..))")
    public Object validateGenerateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String userId = (String) args[0];
        Long expireSeconds = (Long) args[2];

        if (userId == null || userId.trim().isEmpty()) {
            log.warn("参数验证失败 - userId不能为空");
            throw new IllegalArgumentException("userId不能为空");
        }

        if (expireSeconds != null && expireSeconds <= 0) {
            log.warn("参数验证失败 - expireSeconds必须为正数: {}", expireSeconds);
        }

        log.debug("参数验证通过 - userId: {}, expireSeconds: {}", userId, expireSeconds);
        return joinPoint.proceed();
    }

    /**
     * 验证Token验证的参数
     * - tokenValue 不能为空
     */
    @Around("execution(* com.example.tokenservice.service.TokenService.validateToken(..))")
    public Object validateValidateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String tokenValue = (String) args[0];

        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.warn("参数验证失败 - tokenValue不能为空");
            return false;
        }

        log.debug("参数验证通过 - tokenValue长度: {}", tokenValue.length());
        return joinPoint.proceed();
    }

    /**
     * 验证续签Token的参数
     * - oldTokenValue 不能为空
     * - newExpireSeconds 应为正数（如果提供）
     */
    @Around("execution(* com.example.tokenservice.service.TokenService.renewToken(..))")
    public Object validateRenewToken(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String oldTokenValue = (String) args[0];
        Long newExpireSeconds = (Long) args[1];

        if (oldTokenValue == null || oldTokenValue.trim().isEmpty()) {
            log.warn("参数验证失败 - oldTokenValue不能为空");
            return null;
        }

        if (newExpireSeconds != null && newExpireSeconds <= 0) {
            log.warn("参数验证失败 - newExpireSeconds必须为正数: {}", newExpireSeconds);
        }

        log.debug("参数验证通过 - newExpireSeconds: {}", newExpireSeconds);
        return joinPoint.proceed();
    }

    /**
     * 验证获取Token信息的参数
     * - tokenValue 不能为空
     */
    @Around("execution(* com.example.tokenservice.service.TokenService.getTokenInfo(..))")
    public Object validateGetTokenInfo(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String tokenValue = (String) args[0];

        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.warn("参数验证失败 - tokenValue不能为空");
            return java.util.Optional.empty();
        }

        log.debug("参数验证通过 - tokenValue长度: {}", tokenValue.length());
        return joinPoint.proceed();
    }

    /**
     * 验证作废Token的参数
     * - tokenValue 不能为空
     */
    @Around("execution(* com.example.tokenservice.service.TokenService.invalidateToken(..))")
    public Object validateInvalidateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String tokenValue = (String) args[0];

        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.warn("参数验证失败 - tokenValue不能为空");
            return false;
        }

        log.debug("参数验证通过 - tokenValue长度: {}", tokenValue.length());
        return joinPoint.proceed();
    }

    /**
     * 验证解析Token Claims的参数
     * - tokenValue 不能为空
     */
    @Around("execution(* com.example.tokenservice.service.TokenService.parseClaimsQuietly(..))")
    public Object validateParseClaimsQuietly(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String tokenValue = (String) args[0];

        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.warn("参数验证失败 - tokenValue不能为空");
            return null;
        }

        log.debug("参数验证通过 - tokenValue长度: {}", tokenValue.length());
        return joinPoint.proceed();
    }
}
