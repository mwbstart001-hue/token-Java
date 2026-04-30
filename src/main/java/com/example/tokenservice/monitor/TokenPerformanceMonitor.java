package com.example.tokenservice.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TokenPerformanceMonitor {

    private static final Logger log = LoggerFactory.getLogger(TokenPerformanceMonitor.class);

    private final ConcurrentHashMap<String, StrategyPerformanceStats> generateStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StrategyPerformanceStats> validateStats = new ConcurrentHashMap<>();

    public static class StrategyPerformanceStats {
        private final String strategyName;
        private final AtomicLong totalCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong totalTimeMs = new AtomicLong(0);
        private final AtomicLong minTimeMs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTimeMs = new AtomicLong(0);

        public StrategyPerformanceStats(String strategyName) {
            this.strategyName = strategyName;
        }

        public void record(long timeMs, boolean isError) {
            totalCount.incrementAndGet();
            totalTimeMs.addAndGet(timeMs);
            
            if (isError) {
                errorCount.incrementAndGet();
            }

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

        public long getErrorCount() {
            return errorCount.get();
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

        public double getErrorRate() {
            long count = totalCount.get();
            if (count == 0) {
                return 0.0;
            }
            return (double) errorCount.get() / count * 100;
        }

        public void reset() {
            totalCount.set(0);
            errorCount.set(0);
            totalTimeMs.set(0);
            minTimeMs.set(Long.MAX_VALUE);
            maxTimeMs.set(0);
        }

        @Override
        public String toString() {
            return String.format(
                "Strategy: %s, Count: %d, Errors: %d (%.1f%%), Total: %dms, Avg: %dms, Min: %dms, Max: %dms",
                strategyName, getTotalCount(), getErrorCount(), getErrorRate(),
                getTotalTimeMs(), getAvgTimeMs(), getMinTimeMs(), getMaxTimeMs()
            );
        }
    }

    public void recordGenerate(String strategyName, long timeMs) {
        recordGenerate(strategyName, timeMs, false);
    }

    public void recordGenerate(String strategyName, long timeMs, boolean isError) {
        StrategyPerformanceStats stats = generateStats.computeIfAbsent(
            strategyName, k -> new StrategyPerformanceStats(strategyName)
        );
        stats.record(timeMs, isError);
        log.debug("策略 {} 生成耗时: {}ms, 错误: {}", strategyName, timeMs, isError);
    }

    public void recordValidate(String strategyName, long timeMs) {
        recordValidate(strategyName, timeMs, false);
    }

    public void recordValidate(String strategyName, long timeMs, boolean isError) {
        StrategyPerformanceStats stats = validateStats.computeIfAbsent(
            strategyName, k -> new StrategyPerformanceStats(strategyName)
        );
        stats.record(timeMs, isError);
        log.debug("策略 {} 验证耗时: {}ms, 错误: {}", strategyName, timeMs, isError);
    }

    public StrategyPerformanceStats getGenerateStats(String strategyName) {
        return generateStats.get(strategyName);
    }

    public StrategyPerformanceStats getValidateStats(String strategyName) {
        return validateStats.get(strategyName);
    }

    public ConcurrentHashMap<String, StrategyPerformanceStats> getAllGenerateStats() {
        return generateStats;
    }

    public ConcurrentHashMap<String, StrategyPerformanceStats> getAllValidateStats() {
        return validateStats;
    }

    public void resetAllGenerateStats() {
        for (StrategyPerformanceStats stats : generateStats.values()) {
            stats.reset();
        }
        log.info("生成性能统计已重置");
    }

    public void resetAllValidateStats() {
        for (StrategyPerformanceStats stats : validateStats.values()) {
            stats.reset();
        }
        log.info("验证性能统计已重置");
    }

    public void dumpAllStats() {
        log.info("========== 策略性能统计（生成）==========");
        for (StrategyPerformanceStats stats : generateStats.values()) {
            log.info(stats.toString());
        }
        log.info("========== 策略性能统计（验证）==========");
        for (StrategyPerformanceStats stats : validateStats.values()) {
            log.info(stats.toString());
        }
        log.info("==========================================");
    }
}
