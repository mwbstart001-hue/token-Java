package com.example.tokenservice.aspect;

import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.event.TokenOperationEvent;
import com.example.tokenservice.model.TokenOperationType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Token操作统计切面
 * 使用AOP拦截TokenService的操作，记录统计信息
 * 仅当token.statistics.enabled=true时启用
 * 无效Token操作不触发计数
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "token.statistics", name = "enabled", havingValue = "true")
public class TokenStatisticsAspect {

    private static final Logger log = LoggerFactory.getLogger(TokenStatisticsAspect.class);

    private final ApplicationEventPublisher eventPublisher;
    private final TokenProperties tokenProperties;

    public TokenStatisticsAspect(ApplicationEventPublisher eventPublisher, TokenProperties tokenProperties) {
        this.eventPublisher = eventPublisher;
        this.tokenProperties = tokenProperties;
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.generateToken(..))")
    public Object aroundGenerateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String userId = (String) args[0];
        
        log.debug("拦截Token生成操作 - userId: {}", userId);
        
        Object result = joinPoint.proceed();
        
        if (result != null) {
            String tokenValue = (String) result;
            publishEvent(userId, null, tokenValue, TokenOperationType.GENERATE, true, null);
            log.debug("Token生成统计已记录 - userId: {}", userId);
        }
        
        return result;
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.validateToken(..))")
    public Object aroundValidateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String tokenValue = (String) args[0];
        
        log.debug("拦截Token验证操作");
        
        Object result = joinPoint.proceed();
        boolean success = (Boolean) result;
        
        if (success) {
            publishEvent("unknown", null, tokenValue, TokenOperationType.VALIDATE, true, null);
            log.debug("Token验证统计已记录 - success: {}", success);
        } else {
            if (tokenProperties.getStatistics().isRecordInvalidTokens()) {
                publishEvent("unknown", null, tokenValue, TokenOperationType.VALIDATE, false, "Token无效");
            }
            log.debug("Token验证失败，不记录统计");
        }
        
        return result;
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.renewToken(..))")
    public Object aroundRenewToken(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String oldTokenValue = (String) args[0];
        
        log.debug("拦截Token续签操作");
        
        Object result = joinPoint.proceed();
        
        if (result != null) {
            String newTokenValue = (String) result;
            publishEvent("unknown", null, newTokenValue, TokenOperationType.RENEW, true, null);
            log.debug("Token续签统计已记录");
        }
        
        return result;
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.invalidateToken(..))")
    public Object aroundInvalidateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String tokenValue = (String) args[0];
        
        log.debug("拦截Token作废操作");
        
        Object result = joinPoint.proceed();
        boolean success = (Boolean) result;
        
        if (success) {
            publishEvent("unknown", null, tokenValue, TokenOperationType.INVALIDATE, true, null);
            log.debug("Token作废统计已记录 - success: {}", success);
        }
        
        return result;
    }

    private void publishEvent(String userId, String jwtId, String tokenValue,
                               TokenOperationType operationType, boolean success,
                               String failureReason) {
        try {
            TokenOperationEvent event = new TokenOperationEvent(
                    this,
                    userId,
                    jwtId,
                    tokenValue,
                    operationType,
                    success,
                    failureReason,
                    null,
                    null
            );
            eventPublisher.publishEvent(event);
            log.debug("事件已发布 - type: {}, success: {}", operationType, success);
        } catch (Exception e) {
            log.warn("发布Token操作事件失败: {}", e.getMessage());
        }
    }
}
