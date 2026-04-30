package com.example.tokenservice.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class TokenPerformanceMonitorTest {

    private TokenPerformanceMonitor performanceMonitor;

    @BeforeEach
    void setUp() {
        performanceMonitor = new TokenPerformanceMonitor();
    }

    @Test
    void testRecordGenerate_ShouldRecordStats() {
        String strategyName = "RS256TokenGenerator";
        
        performanceMonitor.recordGenerate(strategyName, 100, false);
        performanceMonitor.recordGenerate(strategyName, 150, false);
        performanceMonitor.recordGenerate(strategyName, 200, false);
        
        TokenPerformanceMonitor.StrategyPerformanceStats stats = 
                performanceMonitor.getGenerateStats(strategyName);
        
        assertNotNull(stats);
        assertEquals(3, stats.getTotalCount());
        assertEquals(0, stats.getErrorCount());
        assertEquals(450, stats.getTotalTimeMs());
        assertEquals(150, stats.getAvgTimeMs());
        assertEquals(100, stats.getMinTimeMs());
        assertEquals(200, stats.getMaxTimeMs());
        assertEquals(0.0, stats.getErrorRate());
    }

    @Test
    void testRecordGenerate_WithError_ShouldRecordError() {
        String strategyName = "HS256TokenGenerator";
        
        performanceMonitor.recordGenerate(strategyName, 100, false);
        performanceMonitor.recordGenerate(strategyName, 50, true);
        performanceMonitor.recordGenerate(strategyName, 150, true);
        
        TokenPerformanceMonitor.StrategyPerformanceStats stats = 
                performanceMonitor.getGenerateStats(strategyName);
        
        assertNotNull(stats);
        assertEquals(3, stats.getTotalCount());
        assertEquals(2, stats.getErrorCount());
        assertEquals(300, stats.getTotalTimeMs());
        assertEquals(100, stats.getAvgTimeMs());
        assertEquals(50, stats.getMinTimeMs());
        assertEquals(150, stats.getMaxTimeMs());
        assertEquals(66.7, stats.getErrorRate(), 0.1);
    }

    @Test
    void testRecordValidate_ShouldRecordStats() {
        String strategyName = "RS256TokenValidator";
        
        performanceMonitor.recordValidate(strategyName, 50);
        performanceMonitor.recordValidate(strategyName, 75);
        performanceMonitor.recordValidate(strategyName, 100);
        
        TokenPerformanceMonitor.StrategyPerformanceStats stats = 
                performanceMonitor.getValidateStats(strategyName);
        
        assertNotNull(stats);
        assertEquals(3, stats.getTotalCount());
        assertEquals(0, stats.getErrorCount());
        assertEquals(225, stats.getTotalTimeMs());
        assertEquals(75, stats.getAvgTimeMs());
        assertEquals(50, stats.getMinTimeMs());
        assertEquals(100, stats.getMaxTimeMs());
    }

    @Test
    void testRecordValidate_WithError_ShouldRecordError() {
        String strategyName = "SimpleTokenValidator";
        
        performanceMonitor.recordValidate(strategyName, 30, false);
        performanceMonitor.recordValidate(strategyName, 60, true);
        
        TokenPerformanceMonitor.StrategyPerformanceStats stats = 
                performanceMonitor.getValidateStats(strategyName);
        
        assertNotNull(stats);
        assertEquals(2, stats.getTotalCount());
        assertEquals(1, stats.getErrorCount());
        assertEquals(50.0, stats.getErrorRate(), 0.01);
    }

    @Test
    void testGetGenerateStats_ForNonExistentStrategy_ShouldReturnNull() {
        TokenPerformanceMonitor.StrategyPerformanceStats stats = 
                performanceMonitor.getGenerateStats("NonExistentStrategy");
        
        assertNull(stats);
    }

    @Test
    void testGetValidateStats_ForNonExistentStrategy_ShouldReturnNull() {
        TokenPerformanceMonitor.StrategyPerformanceStats stats = 
                performanceMonitor.getValidateStats("NonExistentStrategy");
        
        assertNull(stats);
    }

    @Test
    void testGetAllGenerateStats_ShouldReturnAllStats() {
        performanceMonitor.recordGenerate("Strategy1", 100);
        performanceMonitor.recordGenerate("Strategy2", 200);
        
        ConcurrentHashMap<String, TokenPerformanceMonitor.StrategyPerformanceStats> allStats = 
                performanceMonitor.getAllGenerateStats();
        
        assertNotNull(allStats);
        assertEquals(2, allStats.size());
        assertTrue(allStats.containsKey("Strategy1"));
        assertTrue(allStats.containsKey("Strategy2"));
    }

    @Test
    void testGetAllValidateStats_ShouldReturnAllStats() {
        performanceMonitor.recordValidate("Validator1", 50);
        performanceMonitor.recordValidate("Validator2", 100);
        
        ConcurrentHashMap<String, TokenPerformanceMonitor.StrategyPerformanceStats> allStats = 
                performanceMonitor.getAllValidateStats();
        
        assertNotNull(allStats);
        assertEquals(2, allStats.size());
        assertTrue(allStats.containsKey("Validator1"));
        assertTrue(allStats.containsKey("Validator2"));
    }

    @Test
    void testResetAllGenerateStats_ShouldResetStats() {
        String strategyName = "TestGenerator";
        
        performanceMonitor.recordGenerate(strategyName, 100);
        performanceMonitor.recordGenerate(strategyName, 200);
        
        assertEquals(2, performanceMonitor.getGenerateStats(strategyName).getTotalCount());
        
        performanceMonitor.resetAllGenerateStats();
        
        assertEquals(0, performanceMonitor.getGenerateStats(strategyName).getTotalCount());
        assertEquals(0, performanceMonitor.getGenerateStats(strategyName).getErrorCount());
        assertEquals(0, performanceMonitor.getGenerateStats(strategyName).getTotalTimeMs());
    }

    @Test
    void testResetAllValidateStats_ShouldResetStats() {
        String strategyName = "TestValidator";
        
        performanceMonitor.recordValidate(strategyName, 50);
        performanceMonitor.recordValidate(strategyName, 100, true);
        
        assertEquals(2, performanceMonitor.getValidateStats(strategyName).getTotalCount());
        assertEquals(1, performanceMonitor.getValidateStats(strategyName).getErrorCount());
        
        performanceMonitor.resetAllValidateStats();
        
        assertEquals(0, performanceMonitor.getValidateStats(strategyName).getTotalCount());
        assertEquals(0, performanceMonitor.getValidateStats(strategyName).getErrorCount());
    }

    @Test
    void testStrategyPerformanceStats_ToString_ShouldIncludeAllInfo() {
        TokenPerformanceMonitor.StrategyPerformanceStats stats = 
                new TokenPerformanceMonitor.StrategyPerformanceStats("TestStrategy");
        
        stats.record(100, false);
        stats.record(200, true);
        stats.record(300, false);
        
        String result = stats.toString();
        
        assertTrue(result.contains("TestStrategy"));
        assertTrue(result.contains("Count: 3"));
        assertTrue(result.contains("Errors: 1"));
        assertTrue(result.contains("Avg: 200ms"));
        assertTrue(result.contains("Min: 100ms"));
        assertTrue(result.contains("Max: 300ms"));
    }

    @Test
    void testStrategyPerformanceStats_WithZeroCount_ShouldReturnZeroAvg() {
        TokenPerformanceMonitor.StrategyPerformanceStats stats = 
                new TokenPerformanceMonitor.StrategyPerformanceStats("EmptyStats");
        
        assertEquals(0, stats.getTotalCount());
        assertEquals(0, stats.getAvgTimeMs());
        assertEquals(0, stats.getMinTimeMs());
        assertEquals(0, stats.getMaxTimeMs());
        assertEquals(0.0, stats.getErrorRate());
    }

    @Test
    void testMultipleStrategies_ShouldSeparateStats() {
        performanceMonitor.recordGenerate("StrategyA", 100);
        performanceMonitor.recordGenerate("StrategyB", 200);
        performanceMonitor.recordGenerate("StrategyA", 150);
        
        TokenPerformanceMonitor.StrategyPerformanceStats statsA = 
                performanceMonitor.getGenerateStats("StrategyA");
        TokenPerformanceMonitor.StrategyPerformanceStats statsB = 
                performanceMonitor.getGenerateStats("StrategyB");
        
        assertEquals(2, statsA.getTotalCount());
        assertEquals(250, statsA.getTotalTimeMs());
        assertEquals(125, statsA.getAvgTimeMs());
        
        assertEquals(1, statsB.getTotalCount());
        assertEquals(200, statsB.getTotalTimeMs());
        assertEquals(200, statsB.getAvgTimeMs());
    }

    @Test
    void testGenerateAndValidate_ShouldBeSeparate() {
        String strategyName = "SharedStrategyName";
        
        performanceMonitor.recordGenerate(strategyName, 100);
        performanceMonitor.recordValidate(strategyName, 50);
        
        TokenPerformanceMonitor.StrategyPerformanceStats generateStats = 
                performanceMonitor.getGenerateStats(strategyName);
        TokenPerformanceMonitor.StrategyPerformanceStats validateStats = 
                performanceMonitor.getValidateStats(strategyName);
        
        assertEquals(1, generateStats.getTotalCount());
        assertEquals(100, generateStats.getTotalTimeMs());
        
        assertEquals(1, validateStats.getTotalCount());
        assertEquals(50, validateStats.getTotalTimeMs());
    }

    @Test
    void testReset_ShouldNotAffectOtherType() {
        String strategyName = "TestBoth";
        
        performanceMonitor.recordGenerate(strategyName, 100);
        performanceMonitor.recordValidate(strategyName, 50);
        
        performanceMonitor.resetAllGenerateStats();
        
        assertEquals(0, performanceMonitor.getGenerateStats(strategyName).getTotalCount());
        assertEquals(1, performanceMonitor.getValidateStats(strategyName).getTotalCount());
    }

    @Test
    void testRecordGenerate_DefaultIsSuccess() {
        String strategyName = "DefaultSuccess";
        
        performanceMonitor.recordGenerate(strategyName, 100);
        
        TokenPerformanceMonitor.StrategyPerformanceStats stats = 
                performanceMonitor.getGenerateStats(strategyName);
        
        assertEquals(0, stats.getErrorCount());
        assertEquals(0.0, stats.getErrorRate());
    }

    @Test
    void testRecordValidate_DefaultIsSuccess() {
        String strategyName = "DefaultSuccessValidator";
        
        performanceMonitor.recordValidate(strategyName, 50);
        
        TokenPerformanceMonitor.StrategyPerformanceStats stats = 
                performanceMonitor.getValidateStats(strategyName);
        
        assertEquals(0, stats.getErrorCount());
        assertEquals(0.0, stats.getErrorRate());
    }
}
