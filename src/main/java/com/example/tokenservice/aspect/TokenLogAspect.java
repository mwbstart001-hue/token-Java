package com.example.tokenservice.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Token操作日志切面
 * 使用AOP统一处理TokenService的方法日志
 * 解耦业务逻辑与日志记录
 * 
 * 职责：
 * 1. 方法入口日志：记录方法名、参数、调用者信息
 * 2. 方法出口日志：记录返回值、执行时间
 * 3. 异常日志：记录异常信息和堆栈
 */
@Aspect
@Component
public class TokenLogAspect {

    private static final Logger log = LoggerFactory.getLogger(TokenLogAspect.class);

    @Around("execution(* com.example.tokenservice.service.TokenService.generateToken(..))")
    public Object logGenerateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "生成Token");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.validateToken(..))")
    public Object logValidateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "验证Token");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.renewToken(..))")
    public Object logRenewToken(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "续签Token");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.getTokenInfo(..))")
    public Object logGetTokenInfo(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "获取Token信息");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.invalidateToken(..))")
    public Object logInvalidateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "作废Token");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.clearExpiredTokens(..))")
    public Object logClearExpiredTokens(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "清理过期Token");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.parseClaimsQuietly(..))")
    public Object logParseClaimsQuietly(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "解析Token Claims");
    }

    /**
     * 通用的方法执行日志记录
     * 
     * @param joinPoint 连接点
     * @param operationName 操作名称
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    private Object logMethodExecution(ProceedingJoinPoint joinPoint, String operationName) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.trace("【入口】{} - 方法: {}, 参数: {}", operationName, methodName, Arrays.toString(args));

        try {
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;

            String resultDesc = buildResultDescription(result);
            log.trace("【出口】{} - 方法: {}, 耗时: {}ms, 结果: {}", 
                    operationName, methodName, executionTime, resultDesc);

            return result;

        } catch (Throwable throwable) {
            long executionTime = System.currentTimeMillis() - startTime;

            log.error("【异常】{} - 方法: {}, 耗时: {}ms, 异常: {}, 消息: {}", 
                    operationName, methodName, executionTime, 
                    throwable.getClass().getSimpleName(), throwable.getMessage(), throwable);

            throw throwable;
        }
    }

    /**
     * 构建结果描述
     * 对于敏感数据（如Token值）做脱敏处理
     */
    private String buildResultDescription(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof String) {
            String str = (String) result;
            if (str.length() > 20) {
                return str.substring(0, 20) + "...";
            }
            return str;
        }
        if (result instanceof Boolean) {
            return String.valueOf(result);
        }
        return result.getClass().getSimpleName();
    }
}
