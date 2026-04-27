package com.example.tokenservice.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token性能监控切面
 * 使用AOP统一监控TokenService方法的性能
 * 可通过配置 token.performance.monitor.enabled 控制开关
 * 
 * 职责：
 * 1. 监控方法执行时间
 * 2. 收集性能统计数据（总次数、总时间、平均时间、最慢执行）
 * 3. 当执行时间超过阈值时发出警告
 * 4. 提供重置统计数据的能力
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "token.performance.monitor", name = "enabled", havingValue = "true", matchIfMissing = false)
public class TokenPerformanceAspect {

    private static final Logger log = LoggerFactory.getLogger(TokenPerformanceAspect.class);

    private static final long WARN_THRESHOLD_MS = 1000;

    private final ConcurrentHashMap<String, MethodPerformanceStats> statsMap = new ConcurrentHashMap<>();

    /**
     * 方法性能统计数据结构
     * 线程安全设计
     */
    public static class MethodPerformanceStats {
        private final String methodName;
        private final AtomicLong totalCount = new AtomicLong(0);
        private final AtomicLong totalTimeMs = new AtomicLong(0);
        private final AtomicLong minTimeMs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTimeMs = new AtomicLong(0);

        public MethodPerformanceStats(String methodName) {
            this.methodName = methodName;
        }

        public void recordExecution(long timeMs) {
            totalCount.incrementAndGet();
            totalTimeMs.addAndGet(timeMs);

            long currentMin;
            do {
                currentMin = minTimeMs.get();
            } while (timeMs < currentMin && !minTimeMs.compareAndSet(currentMin, timeMs));

            long currentMax;
            do {
                currentMax = maxTimeMs.get();
            } while (timeMs > currentMax && !maxTimeMs.compareAndSet(currentMax, timeMs));
        }

        public long getTotalCount() {
            return totalCount.get();
        }

        public long getTotalTimeMs() {
            return totalTimeMs.get();
        }

        public long getAvgTimeMs() {
            long count = totalCount.get();
            return count > 0 ? totalTimeMs.get() / count : 0;
        }

        public long getMinTimeMs() {
            long min = minTimeMs.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }

        public long getMaxTimeMs() {
            return maxTimeMs.get();
        }

        public void reset() {
            totalCount.set(0);
            totalTimeMs.set(0);
            minTimeMs.set(Long.MAX_VALUE);
            maxTimeMs.set(0);
        }

        @Override
        public String toString() {
            return String.format(
                "Method: %s, Count: %d, Total: %dms, Avg: %dms, Min: %dms, Max: %dms",
                methodName, getTotalCount(), getTotalTimeMs(), getAvgTimeMs(), getMinTimeMs(), getMaxTimeMs()
            );
        }
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.generateToken(..))")
    public Object monitorGenerateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethod(joinPoint, "generateToken");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.validateToken(..))")
    public Object monitorValidateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethod(joinPoint, "validateToken");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.renewToken(..))")
    public Object monitorRenewToken(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethod(joinPoint, "renewToken");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.getTokenInfo(..))")
    public Object monitorGetTokenInfo(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethod(joinPoint, "getTokenInfo");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.invalidateToken(..))")
    public Object monitorInvalidateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethod(joinPoint, "invalidateToken");
    }

    @Around("execution(* com.example.tokenservice.service.TokenService.clearExpiredTokens(..))")
    public Object monitorClearExpiredTokens(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethod(joinPoint, "clearExpiredTokens");
    }

    /**
     * 通用的方法性能监控
     */
    private Object monitorMethod(ProceedingJoinPoint joinPoint, String methodName) throws Throwable {
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            recordPerformance(methodName, executionTime);

            if (executionTime > WARN_THRESHOLD_MS) {
                log.warn("【性能警告】方法 {} 执行时间超过阈值: {}ms (阈值: {}ms)", 
                        methodName, executionTime, WARN_THRESHOLD_MS);
            } else {
                log.debug("【性能监控】方法 {} 执行时间: {}ms", methodName, executionTime);
            }

            return result;

        } catch (Throwable throwable) {
            long executionTime = System.currentTimeMillis() - startTime;
            recordPerformance(methodName, executionTime);

            log.debug("【性能监控】方法 {} 执行时间(异常): {}ms", methodName, executionTime);
            throw throwable;
        }
    }

    /**
     * 记录性能数据
     */
    private void recordPerformance(String methodName, long executionTime) {
        MethodPerformanceStats stats = statsMap.computeIfAbsent(
            methodName, k -> new MethodPerformanceStats(methodName)
        );
        stats.recordExecution(executionTime);
    }

    /**
     * 获取指定方法的性能统计
     */
    public MethodPerformanceStats getStats(String methodName) {
        return statsMap.get(methodName);
    }

    /**
     * 输出所有方法的性能统计
     */
    public void dumpAllStats() {
        log.info("========== 性能统计数据 ==========");
        for (MethodPerformanceStats stats : statsMap.values()) {
            log.info(stats.toString());
        }
        log.info("==================================");
    }

    /**
     * 重置所有统计数据
     */
    public void resetAllStats() {
        for (MethodPerformanceStats stats : statsMap.values()) {
            stats.reset();
        }
        log.info("性能统计数据已重置");
    }
}
