package com.example.tokenservice;

import com.example.tokenservice.config.JwtKeyManager;
import com.example.tokenservice.dto.TokenStatisticsSummary;
import com.example.tokenservice.model.TokenOperationType;
import com.example.tokenservice.model.TokenStatistics;
import com.example.tokenservice.repository.TokenStatisticsRepository;
import com.example.tokenservice.service.TokenService;
import com.example.tokenservice.service.TokenStatisticsService;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "token.statistics.enabled=true",
    "token.statistics.record-invalid-tokens=false"
})
class TokenStatisticsLinkIntegrationTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private TokenStatisticsService statisticsService;

    @Autowired
    private TokenStatisticsRepository statisticsRepository;

    @Autowired
    private JwtKeyManager jwtKeyManager;

    @BeforeEach
    void setUp() {
        statisticsRepository.deleteAll();
        Awaitility.setDefaultTimeout(10, TimeUnit.SECONDS);
        Awaitility.setDefaultPollInterval(100, TimeUnit.MILLISECONDS);
    }

    private ConditionFactory awaitAsync() {
        return await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS);
    }

    private void waitForRecords(int expectedCount) {
        awaitAsync().until(() -> statisticsRepository.count() == expectedCount);
    }

    private void waitForRecordsByType(String userId, TokenOperationType type, long expectedCount) {
        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);
        awaitAsync().until(() -> {
            List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, start, end);
            return stats.stream()
                    .filter(s -> s.getOperationType() == type)
                    .mapToLong(TokenStatisticsSummary::getTotalCount)
                    .sum() == expectedCount;
        });
    }

    @Test
    void generateToken_ShouldRecordStatistics() {
        String userId = "test-user-001";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String token = tokenService.generateToken(userId, "test-subject", 3600L);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        waitForRecords(1);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, beforeOp, afterOp);

        assertFalse(stats.isEmpty(), "应该有统计记录");

        Optional<TokenStatisticsSummary> generateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.GENERATE)
                .findFirst();

        assertTrue(generateStats.isPresent(), "应该有 GENERATE 类型的统计");
        assertEquals(1L, generateStats.get().getTotalCount());
        assertEquals(1L, generateStats.get().getSuccessCount());
        assertEquals(0L, generateStats.get().getFailureCount());

        List<TokenStatisticsSummary> globalStats = statisticsService.getGlobalStatistics(beforeOp, afterOp);
        Optional<TokenStatisticsSummary> globalGenerate = globalStats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.GENERATE)
                .findFirst();
        assertTrue(globalGenerate.isPresent());
        assertEquals(1L, globalGenerate.get().getTotalCount());
    }

    @Test
    void generateToken_ShouldRecordCorrectUserIdAndJwtId() {
        String userId = "test-user-002";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String token = tokenService.generateToken(userId, "test-subject", 3600L);
        String expectedJwtId = jwtKeyManager.extractJwtIdQuietly(token);

        waitForRecords(1);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatistics> records = statisticsService.getUserRecords(userId, beforeOp, afterOp);

        assertFalse(records.isEmpty());
        assertEquals(1, records.size());

        TokenStatistics record = records.get(0);
        assertEquals(userId, record.getUserId());
        assertEquals(expectedJwtId, record.getJwtId());
        assertEquals(TokenOperationType.GENERATE, record.getOperationType());
        assertTrue(record.isSuccess());
    }

    @Test
    void generateToken_MultipleTimes_ShouldCountEach() {
        String userId = "test-user-003";
        int generateCount = 5;
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        for (int i = 0; i < generateCount; i++) {
            tokenService.generateToken(userId, "subject-" + i, 3600L);
        }

        waitForRecords(generateCount);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, beforeOp, afterOp);
        Optional<TokenStatisticsSummary> generateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.GENERATE)
                .findFirst();

        assertTrue(generateStats.isPresent());
        assertEquals(generateCount, generateStats.get().getTotalCount());
    }

    @Test
    void validateToken_WithValidToken_ShouldRecordStatistics() {
        String userId = "validate-user-001";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String token = tokenService.generateToken(userId, "test", 3600L);
        waitForRecords(1);

        boolean valid = tokenService.validateToken(token);

        assertTrue(valid);

        waitForRecords(2);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, beforeOp, afterOp);

        Optional<TokenStatisticsSummary> validateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.VALIDATE)
                .findFirst();

        assertTrue(validateStats.isPresent(), "应该有 VALIDATE 类型的统计");
        assertEquals(1L, validateStats.get().getTotalCount());
        assertEquals(1L, validateStats.get().getSuccessCount());

        List<TokenStatisticsSummary> globalStats = statisticsService.getGlobalStatistics(beforeOp, afterOp);
        assertTrue(globalStats.stream()
                .anyMatch(s -> s.getOperationType() == TokenOperationType.VALIDATE),
                "全局统计应该包含 VALIDATE 类型");
    }

    @Test
    void validateToken_WithInvalidToken_ShouldNotRecordStatistics() throws InterruptedException {
        String invalidToken = "this-is-an-invalid-token-12345";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        boolean valid = tokenService.validateToken(invalidToken);

        assertFalse(valid);

        Thread.sleep(500);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        long totalRecords = statisticsRepository.count();

        assertEquals(0L, totalRecords, "无效 Token 不应该触发统计记录");

        List<TokenStatisticsSummary> globalStats = statisticsService.getGlobalStatistics(beforeOp, afterOp);
        assertTrue(globalStats.isEmpty(), "全局统计应该为空");
    }

    @Test
    void validateToken_ShouldRecordCorrectUserId() {
        String userId = "validate-user-002";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String token = tokenService.generateToken(userId, "test", 3600L);
        waitForRecords(1);

        tokenService.validateToken(token);
        waitForRecords(2);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatistics> records = statisticsService.getUserRecords(userId, beforeOp, afterOp);

        long validateCount = records.stream()
                .filter(r -> r.getOperationType() == TokenOperationType.VALIDATE)
                .count();

        assertEquals(1L, validateCount);

        Optional<TokenStatistics> validateRecord = records.stream()
                .filter(r -> r.getOperationType() == TokenOperationType.VALIDATE)
                .findFirst();

        assertTrue(validateRecord.isPresent());
        assertEquals(userId, validateRecord.get().getUserId());
    }

    @Test
    void renewToken_ShouldRecordStatistics() {
        String userId = "renew-user-001";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String oldToken = tokenService.generateToken(userId, "test", 3600L);
        waitForRecords(1);

        String newToken = tokenService.renewToken(oldToken, 3600L, true);

        assertNotNull(newToken);
        assertNotEquals(oldToken, newToken);

        waitForRecords(2);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, beforeOp, afterOp);

        Optional<TokenStatisticsSummary> renewStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.RENEW)
                .findFirst();

        assertTrue(renewStats.isPresent(), "应该有 RENEW 类型的统计");
        assertEquals(1L, renewStats.get().getTotalCount());
        assertEquals(1L, renewStats.get().getSuccessCount());
    }

    @Test
    void renewToken_ShouldRecordNewJwtId() {
        String userId = "renew-user-002";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String oldToken = tokenService.generateToken(userId, "test", 3600L);
        String oldJwtId = jwtKeyManager.extractJwtIdQuietly(oldToken);
        waitForRecords(1);

        String newToken = tokenService.renewToken(oldToken, 3600L, true);
        String newJwtId = jwtKeyManager.extractJwtIdQuietly(newToken);

        assertNotEquals(oldJwtId, newJwtId);

        waitForRecords(2);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatistics> records = statisticsService.getUserRecords(userId, beforeOp, afterOp);

        Optional<TokenStatistics> renewRecord = records.stream()
                .filter(r -> r.getOperationType() == TokenOperationType.RENEW)
                .findFirst();

        assertTrue(renewRecord.isPresent());
        assertEquals(newJwtId, renewRecord.get().getJwtId(), "续签记录应该记录新的 jwtId");
        assertEquals(userId, renewRecord.get().getUserId());
    }

    @Test
    void renewToken_WithInvalidToken_ShouldNotRecordStatistics() throws InterruptedException {
        String invalidToken = "invalid-token-for-renew";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String newToken = tokenService.renewToken(invalidToken, 3600L, true);

        assertNull(newToken);

        Thread.sleep(500);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        long totalRecords = statisticsRepository.count();
        assertEquals(0L, totalRecords, "无效 Token 续签不应该触发统计");
    }

    @Test
    void invalidateToken_ShouldRecordStatistics() {
        String userId = "invalidate-user-001";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String token = tokenService.generateToken(userId, "test", 3600L);
        waitForRecords(1);

        boolean invalidated = tokenService.invalidateToken(token);

        assertTrue(invalidated);

        waitForRecords(2);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, beforeOp, afterOp);

        Optional<TokenStatisticsSummary> invalidateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.INVALIDATE)
                .findFirst();

        assertTrue(invalidateStats.isPresent(), "应该有 INVALIDATE 类型的统计");
        assertEquals(1L, invalidateStats.get().getTotalCount());
    }

    @Test
    void invalidateToken_ShouldRecordCorrectInfo() {
        String userId = "invalidate-user-002";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String token = tokenService.generateToken(userId, "test", 3600L);
        String expectedJwtId = jwtKeyManager.extractJwtIdQuietly(token);
        waitForRecords(1);

        tokenService.invalidateToken(token);
        waitForRecords(2);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatistics> records = statisticsService.getUserRecords(userId, beforeOp, afterOp);

        Optional<TokenStatistics> invalidateRecord = records.stream()
                .filter(r -> r.getOperationType() == TokenOperationType.INVALIDATE)
                .findFirst();

        assertTrue(invalidateRecord.isPresent());
        assertEquals(userId, invalidateRecord.get().getUserId());
        assertEquals(expectedJwtId, invalidateRecord.get().getJwtId());
    }

    @Test
    void fullTokenLifecycle_ShouldRecordAllOperations() {
        String userId = "full-lifecycle-user";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String token = tokenService.generateToken(userId, "test", 3600L);
        waitForRecordsByType(userId, TokenOperationType.GENERATE, 1);

        tokenService.validateToken(token);
        waitForRecordsByType(userId, TokenOperationType.VALIDATE, 1);

        tokenService.validateToken(token);
        waitForRecordsByType(userId, TokenOperationType.VALIDATE, 2);

        String newToken = tokenService.renewToken(token, 3600L, true);
        waitForRecordsByType(userId, TokenOperationType.RENEW, 1);

        tokenService.validateToken(newToken);
        waitForRecordsByType(userId, TokenOperationType.VALIDATE, 3);

        tokenService.invalidateToken(newToken);
        waitForRecordsByType(userId, TokenOperationType.INVALIDATE, 1);

        waitForRecords(6);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, beforeOp, afterOp);

        Optional<TokenStatisticsSummary> generateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.GENERATE)
                .findFirst();
        Optional<TokenStatisticsSummary> validateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.VALIDATE)
                .findFirst();
        Optional<TokenStatisticsSummary> renewStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.RENEW)
                .findFirst();
        Optional<TokenStatisticsSummary> invalidateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.INVALIDATE)
                .findFirst();

        assertTrue(generateStats.isPresent());
        assertEquals(1L, generateStats.get().getTotalCount());

        assertTrue(validateStats.isPresent());
        assertEquals(3L, validateStats.get().getTotalCount());

        assertTrue(renewStats.isPresent());
        assertEquals(1L, renewStats.get().getTotalCount());

        assertTrue(invalidateStats.isPresent());
        assertEquals(1L, invalidateStats.get().getTotalCount());

        List<TokenStatisticsSummary> globalStats = statisticsService.getGlobalStatistics(beforeOp, afterOp);
        long globalTotal = globalStats.stream()
                .mapToLong(TokenStatisticsSummary::getTotalCount)
                .sum();
        assertEquals(6L, globalTotal);
    }

    @Test
    void multipleUsers_ShouldBeSeparatedInStatistics() {
        String user1 = "multi-user-001";
        String user2 = "multi-user-002";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String token1 = tokenService.generateToken(user1, "test", 3600L);
        waitForRecordsByType(user1, TokenOperationType.GENERATE, 1);

        tokenService.validateToken(token1);
        waitForRecordsByType(user1, TokenOperationType.VALIDATE, 1);

        String token2 = tokenService.generateToken(user2, "test", 3600L);
        waitForRecordsByType(user2, TokenOperationType.GENERATE, 1);

        tokenService.validateToken(token2);
        waitForRecordsByType(user2, TokenOperationType.VALIDATE, 1);

        tokenService.invalidateToken(token2);
        waitForRecordsByType(user2, TokenOperationType.INVALIDATE, 1);

        waitForRecords(5);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatisticsSummary> user1Stats = statisticsService.getUserStatistics(user1, beforeOp, afterOp);
        List<TokenStatisticsSummary> user2Stats = statisticsService.getUserStatistics(user2, beforeOp, afterOp);

        long user1Total = user1Stats.stream().mapToLong(TokenStatisticsSummary::getTotalCount).sum();
        long user2Total = user2Stats.stream().mapToLong(TokenStatisticsSummary::getTotalCount).sum();

        assertEquals(2L, user1Total);
        assertEquals(3L, user2Total);

        Optional<TokenStatisticsSummary> user2Invalidate = user2Stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.INVALIDATE)
                .findFirst();
        assertTrue(user2Invalidate.isPresent());

        Optional<TokenStatisticsSummary> user1Invalidate = user1Stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.INVALIDATE)
                .findFirst();
        assertFalse(user1Invalidate.isPresent(), "user1 没有作废操作");
    }

    @Test
    void getAllStatistics_ShouldAggregateAllUsers() {
        String userA = "agg-user-a";
        String userB = "agg-user-b";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        tokenService.generateToken(userA, "test", 3600L);
        waitForRecordsByType(userA, TokenOperationType.GENERATE, 1);

        tokenService.generateToken(userA, "test", 3600L);
        waitForRecordsByType(userA, TokenOperationType.GENERATE, 2);

        tokenService.generateToken(userB, "test", 3600L);
        waitForRecordsByType(userB, TokenOperationType.GENERATE, 1);

        waitForRecords(3);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatisticsSummary> allStats = statisticsService.getAllStatistics(beforeOp, afterOp);

        long userAGenerateCount = allStats.stream()
                .filter(s -> s.getUserId().equals(userA))
                .filter(s -> s.getOperationType() == TokenOperationType.GENERATE)
                .mapToLong(TokenStatisticsSummary::getTotalCount)
                .sum();

        long userBGenerateCount = allStats.stream()
                .filter(s -> s.getUserId().equals(userB))
                .filter(s -> s.getOperationType() == TokenOperationType.GENERATE)
                .mapToLong(TokenStatisticsSummary::getTotalCount)
                .sum();

        assertEquals(2L, userAGenerateCount);
        assertEquals(1L, userBGenerateCount);
    }

    @Test
    void concurrentOperations_ShouldBeThreadSafe() throws InterruptedException {
        String userId = "concurrent-user";
        int threadCount = 20;
        int operationsPerThread = 5;
        int expectedTotalRecords = threadCount * operationsPerThread * 2;

        CountDownLatch generateLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int j = 0; j < operationsPerThread; j++) {
                            String token = tokenService.generateToken(userId, "test", 3600L);
                            if (token != null) {
                                tokenService.validateToken(token);
                            }
                        }
                        successCount.incrementAndGet();
                    } finally {
                        generateLatch.countDown();
                    }
                }
            });
        }

        generateLatch.await();
        executor.shutdown();

        assertEquals(threadCount, successCount.get());

        LocalDateTime beforeOp = LocalDateTime.now().minusMinutes(1);
        LocalDateTime afterOp = LocalDateTime.now().plusMinutes(1);

        waitForRecords(expectedTotalRecords);

        List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, beforeOp, afterOp);

        Optional<TokenStatisticsSummary> generateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.GENERATE)
                .findFirst();
        Optional<TokenStatisticsSummary> validateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.VALIDATE)
                .findFirst();

        assertTrue(generateStats.isPresent());
        assertTrue(validateStats.isPresent());

        assertEquals(threadCount * operationsPerThread, generateStats.get().getTotalCount());
        assertEquals(threadCount * operationsPerThread, validateStats.get().getTotalCount());
    }

    @Test
    void validate_WithInvalidToken_NotRecorded() throws InterruptedException {
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        tokenService.validateToken("invalid-token-1");
        tokenService.validateToken("invalid-token-2");

        Thread.sleep(500);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        long totalRecords = statisticsRepository.count();
        assertEquals(0L, totalRecords, "无效验证不应该触发统计");
    }

    @Test
    void renew_WithInvalidToken_NotRecorded() throws InterruptedException {
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        tokenService.renewToken("invalid-token", 3600L, true);

        Thread.sleep(500);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        long totalRecords = statisticsRepository.count();
        assertEquals(0L, totalRecords, "无效续签不应该触发统计");
    }

    @Test
    void invalidate_WithInvalidToken_NotRecorded() throws InterruptedException {
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        tokenService.invalidateToken("non-existent-token");

        Thread.sleep(500);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        long totalRecords = statisticsRepository.count();
        assertEquals(0L, totalRecords, "无效作废不应该触发统计");
    }

    @Test
    void mixedOperations_OnlyRecordSuccessful() {
        String userId = "mixed-op-user";
        LocalDateTime beforeOp = LocalDateTime.now().minusSeconds(1);

        String validToken = tokenService.generateToken(userId, "test", 3600L);
        waitForRecordsByType(userId, TokenOperationType.GENERATE, 1);

        tokenService.validateToken(validToken);
        waitForRecordsByType(userId, TokenOperationType.VALIDATE, 1);

        tokenService.validateToken("invalid-token");
        tokenService.validateToken(validToken);
        waitForRecordsByType(userId, TokenOperationType.VALIDATE, 2);

        tokenService.renewToken("invalid-token", 3600L, true);
        tokenService.invalidateToken(validToken);
        waitForRecordsByType(userId, TokenOperationType.INVALIDATE, 1);

        waitForRecords(4);

        LocalDateTime afterOp = LocalDateTime.now().plusSeconds(1);

        List<TokenStatisticsSummary> stats = statisticsService.getUserStatistics(userId, beforeOp, afterOp);

        Optional<TokenStatisticsSummary> generateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.GENERATE)
                .findFirst();
        Optional<TokenStatisticsSummary> validateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.VALIDATE)
                .findFirst();
        Optional<TokenStatisticsSummary> renewStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.RENEW)
                .findFirst();
        Optional<TokenStatisticsSummary> invalidateStats = stats.stream()
                .filter(s -> s.getOperationType() == TokenOperationType.INVALIDATE)
                .findFirst();

        assertTrue(generateStats.isPresent());
        assertEquals(1L, generateStats.get().getTotalCount());

        assertTrue(validateStats.isPresent());
        assertEquals(2L, validateStats.get().getTotalCount());

        assertFalse(renewStats.isPresent(), "无效的续签不应该被统计");

        assertTrue(invalidateStats.isPresent());
        assertEquals(1L, invalidateStats.get().getTotalCount());
    }

    @Test
    void asyncEventProcessing_ShouldBeAsynchronous() throws InterruptedException {
        String userId = "async-test-user";
        
        long startTime = System.currentTimeMillis();
        
        String token = tokenService.generateToken(userId, "test", 3600L);
        
        long operationTime = System.currentTimeMillis() - startTime;
        
        assertTrue(operationTime < 1000, "主操作应该快速返回（异步处理）");
        
        waitForRecords(1);
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        List<TokenStatistics> records = statisticsService.getUserRecords(
            userId, 
            LocalDateTime.now().minusMinutes(1), 
            LocalDateTime.now().plusMinutes(1)
        );
        
        assertFalse(records.isEmpty());
        assertEquals(userId, records.get(0).getUserId());
        assertEquals(TokenOperationType.GENERATE, records.get(0).getOperationType());
    }
}
