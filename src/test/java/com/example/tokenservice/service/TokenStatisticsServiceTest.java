package com.example.tokenservice.service;

import com.example.tokenservice.dto.TokenStatisticsSummary;
import com.example.tokenservice.model.TokenOperationType;
import com.example.tokenservice.model.TokenStatistics;
import com.example.tokenservice.repository.TokenStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统计功能测试用例
 * 测试全局统计的分组查询是否正确工作
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenStatisticsServiceTest {

    @Autowired
    private TokenStatisticsService statisticsService;

    @Autowired
    private TokenStatisticsRepository statisticsRepository;

    @BeforeEach
    void setUp() {
        statisticsRepository.deleteAll();
    }

    /**
     * 测试全局统计 - 验证修复后的分组查询
     * 
     * 问题场景：
     * 插入不同操作类型的统计记录，验证查询结果是否按操作类型正确分组
     * 
     * 数据准备：
     * - GENERATE: 3条
     * - VALIDATE: 5条
     * - RENEW: 2条
     * - INVALIDATE: 1条
     * 
     * 预期结果：
     * 每个操作类型的统计应该正确分组，而不是所有类型都显示总数
     */
    @Test
    void getGlobalStatistics_ShouldGroupByOperationType() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(1);
        LocalDateTime endTime = now.plusHours(1);

        createStatistics("user1", TokenOperationType.GENERATE, true, now.minusMinutes(30));
        createStatistics("user2", TokenOperationType.GENERATE, true, now.minusMinutes(20));
        createStatistics("user3", TokenOperationType.GENERATE, true, now.minusMinutes(10));

        createStatistics("user1", TokenOperationType.VALIDATE, true, now.minusMinutes(25));
        createStatistics("user1", TokenOperationType.VALIDATE, true, now.minusMinutes(15));
        createStatistics("user2", TokenOperationType.VALIDATE, true, now.minusMinutes(5));
        createStatistics("user3", TokenOperationType.VALIDATE, true, now);
        createStatistics("user1", TokenOperationType.VALIDATE, true, now.plusMinutes(5));

        createStatistics("user1", TokenOperationType.RENEW, true, now.minusMinutes(15));
        createStatistics("user2", TokenOperationType.RENEW, true, now);

        createStatistics("user1", TokenOperationType.INVALIDATE, true, now.minusMinutes(10));

        List<TokenStatisticsSummary> globalStats = statisticsService.getGlobalStatistics(startTime, endTime);

        assertEquals(4, globalStats.size(), "应该有4种操作类型的统计");

        for (TokenStatisticsSummary summary : globalStats) {
            switch (summary.getOperationType()) {
                case GENERATE:
                    assertEquals(3, summary.getTotalCount(), "GENERATE 应该有3条");
                    assertEquals(3, summary.getSuccessCount(), "GENERATE 成功数应该是3");
                    assertEquals(0, summary.getFailureCount(), "GENERATE 失败数应该是0");
                    break;
                case VALIDATE:
                    assertEquals(5, summary.getTotalCount(), "VALIDATE 应该有5条");
                    assertEquals(5, summary.getSuccessCount(), "VALIDATE 成功数应该是5");
                    assertEquals(0, summary.getFailureCount(), "VALIDATE 失败数应该是0");
                    break;
                case RENEW:
                    assertEquals(2, summary.getTotalCount(), "RENEW 应该有2条");
                    assertEquals(2, summary.getSuccessCount(), "RENEW 成功数应该是2");
                    assertEquals(0, summary.getFailureCount(), "RENEW 失败数应该是0");
                    break;
                case INVALIDATE:
                    assertEquals(1, summary.getTotalCount(), "INVALIDATE 应该有1条");
                    assertEquals(1, summary.getSuccessCount(), "INVALIDATE 成功数应该是1");
                    assertEquals(0, summary.getFailureCount(), "INVALIDATE 失败数应该是0");
                    break;
            }
        }
    }

    /**
     * 测试全局统计 - 验证不同用户的数据被正确汇总
     */
    @Test
    void getGlobalStatistics_ShouldAggregateAllUsers() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(1);
        LocalDateTime endTime = now.plusHours(1);

        createStatistics("user-A", TokenOperationType.GENERATE, true, now.minusMinutes(30));
        createStatistics("user-B", TokenOperationType.GENERATE, true, now.minusMinutes(20));
        createStatistics("user-C", TokenOperationType.GENERATE, true, now.minusMinutes(10));
        
        createStatistics("user-A", TokenOperationType.VALIDATE, true, now.minusMinutes(15));
        createStatistics("user-B", TokenOperationType.VALIDATE, true, now.minusMinutes(5));

        List<TokenStatisticsSummary> globalStats = statisticsService.getGlobalStatistics(startTime, endTime);

        TokenStatisticsSummary generateStats = findByType(globalStats, TokenOperationType.GENERATE);
        assertNotNull(generateStats);
        assertEquals(3, generateStats.getTotalCount(), "全局统计应该汇总所有用户的GENERATE操作");

        TokenStatisticsSummary validateStats = findByType(globalStats, TokenOperationType.VALIDATE);
        assertNotNull(validateStats);
        assertEquals(2, validateStats.getTotalCount(), "全局统计应该汇总所有用户的VALIDATE操作");
    }

    /**
     * 测试全局统计 - 验证时间范围过滤
     */
    @Test
    void getGlobalStatistics_ShouldFilterByTimeRange() {
        LocalDateTime now = LocalDateTime.now();

        createStatistics("user1", TokenOperationType.GENERATE, true, now.minusDays(2));
        createStatistics("user2", TokenOperationType.GENERATE, true, now.minusHours(1));
        createStatistics("user3", TokenOperationType.GENERATE, true, now);
        createStatistics("user4", TokenOperationType.GENERATE, true, now.plusHours(1));

        LocalDateTime startTime = now.minusHours(2);
        LocalDateTime endTime = now.plusMinutes(30);

        List<TokenStatisticsSummary> globalStats = statisticsService.getGlobalStatistics(startTime, endTime);

        TokenStatisticsSummary stats = findByType(globalStats, TokenOperationType.GENERATE);
        assertNotNull(stats);
        assertEquals(2, stats.getTotalCount(), "应该只统计时间范围内的2条记录");
    }

    /**
     * 测试全局统计 - 验证成功率计算
     */
    @Test
    void getGlobalStatistics_ShouldCalculateSuccessRate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(1);
        LocalDateTime endTime = now.plusHours(1);

        createStatistics("user1", TokenOperationType.VALIDATE, true, now.minusMinutes(30));
        createStatistics("user2", TokenOperationType.VALIDATE, true, now.minusMinutes(20));
        createStatistics("user3", TokenOperationType.VALIDATE, true, now.minusMinutes(10));
        createStatistics("user4", TokenOperationType.VALIDATE, false, now.minusMinutes(5));

        List<TokenStatisticsSummary> globalStats = statisticsService.getGlobalStatistics(startTime, endTime);

        TokenStatisticsSummary stats = findByType(globalStats, TokenOperationType.VALIDATE);
        assertNotNull(stats);
        assertEquals(4, stats.getTotalCount());
        assertEquals(3, stats.getSuccessCount());
        assertEquals(1, stats.getFailureCount());
        assertEquals(75.0, stats.getSuccessRate(), 0.1, "成功率应该是75%");
    }

    /**
     * 测试全局统计 - 空数据场景
     */
    @Test
    void getGlobalStatistics_WithNoData_ShouldReturnEmpty() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(1);
        LocalDateTime endTime = now.plusHours(1);

        List<TokenStatisticsSummary> globalStats = statisticsService.getGlobalStatistics(startTime, endTime);

        assertTrue(globalStats.isEmpty(), "没有数据时应该返回空列表");
    }

    /**
     * 测试获取所有用户统计 - 验证按用户和操作类型分组
     */
    @Test
    void getAllStatistics_ShouldGroupByUserAndOperationType() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(1);
        LocalDateTime endTime = now.plusHours(1);

        createStatistics("user-A", TokenOperationType.GENERATE, true, now.minusMinutes(30));
        createStatistics("user-A", TokenOperationType.VALIDATE, true, now.minusMinutes(20));
        createStatistics("user-A", TokenOperationType.VALIDATE, true, now.minusMinutes(10));
        createStatistics("user-B", TokenOperationType.GENERATE, true, now.minusMinutes(25));
        createStatistics("user-B", TokenOperationType.RENEW, true, now.minusMinutes(15));

        List<TokenStatisticsSummary> allStats = statisticsService.getAllStatistics(startTime, endTime);

        assertEquals(4, allStats.size(), "应该有4条记录（按用户和操作类型分组）");

        long userAGenerate = allStats.stream()
                .filter(s -> "user-A".equals(s.getUserId()) && s.getOperationType() == TokenOperationType.GENERATE)
                .count();
        long userAValidate = allStats.stream()
                .filter(s -> "user-A".equals(s.getUserId()) && s.getOperationType() == TokenOperationType.VALIDATE)
                .count();
        long userBGenerate = allStats.stream()
                .filter(s -> "user-B".equals(s.getUserId()) && s.getOperationType() == TokenOperationType.GENERATE)
                .count();
        long userBRenew = allStats.stream()
                .filter(s -> "user-B".equals(s.getUserId()) && s.getOperationType() == TokenOperationType.RENEW)
                .count();

        assertEquals(1, userAGenerate, "user-A的GENERATE统计应该存在");
        assertEquals(1, userAValidate, "user-A的VALIDATE统计应该存在");
        assertEquals(1, userBGenerate, "user-B的GENERATE统计应该存在");
        assertEquals(1, userBRenew, "user-B的RENEW统计应该存在");
    }

    private TokenStatisticsSummary findByType(List<TokenStatisticsSummary> summaries, TokenOperationType type) {
        return summaries.stream()
                .filter(s -> s.getOperationType() == type)
                .findFirst()
                .orElse(null);
    }

    private void createStatistics(String userId, TokenOperationType operationType, boolean success, LocalDateTime time) {
        TokenStatistics stats = new TokenStatistics();
        stats.setUserId(userId);
        stats.setJwtId("jwt-" + System.currentTimeMillis() + "-" + userId);
        stats.setOperationType(operationType);
        stats.setSuccess(success);
        stats.setOperationTime(time);
        statisticsRepository.save(stats);
    }
}
