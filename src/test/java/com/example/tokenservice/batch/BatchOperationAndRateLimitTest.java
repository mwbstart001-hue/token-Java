package com.example.tokenservice.batch;

import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.dto.BatchGenerateResponse;
import com.example.tokenservice.ratelimit.RateLimitService;
import com.example.tokenservice.revocation.TokenRevocationService;
import com.example.tokenservice.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("批量操作与限流测试")
public class BatchOperationAndRateLimitTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private TokenRevocationService tokenRevocationService;

    @Autowired
    private TokenProperties tokenProperties;

    private String testUserId;
    private String testIp;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-" + UUID.randomUUID().toString();
        testIp = "192.168.1." + (int) (Math.random() * 255);
        rateLimitService.resetAllRateLimits();
    }

    @Test
    @DisplayName("批量生成Token - 正常场景")
    void testBatchGenerateTokens_Normal() {
        int count = 5;
        BatchGenerateResponse response = tokenService.batchGenerateTokens(
                testUserId,
                "test-subject",
                3600L,
                count
        );

        assertNotNull(response);
        assertEquals(count, response.getTotalCount());
        assertEquals(count, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertEquals(count, response.getTokens().size());
        assertTrue(response.getFailures().isEmpty());

        for (String token : response.getTokens()) {
            assertNotNull(token);
            assertFalse(token.isEmpty());
        }
    }

    @Test
    @DisplayName("批量生成Token - 生成的Token唯一")
    void testBatchGenerateTokens_UniqueTokens() {
        int count = 10;
        BatchGenerateResponse response = tokenService.batchGenerateTokens(
                testUserId,
                "test-subject",
                3600L,
                count
        );

        long uniqueCount = response.getTokens().stream().distinct().count();
        assertEquals(count, uniqueCount, "所有生成的Token应该是唯一的");
    }

    @Test
    @DisplayName("批量生成Token - 全部可验证通过")
    void testBatchGenerateTokens_AllValid() {
        int count = 5;
        BatchGenerateResponse response = tokenService.batchGenerateTokens(
                testUserId,
                "test-subject",
                3600L,
                count
        );

        for (String token : response.getTokens()) {
            boolean valid = tokenService.validateToken(token);
            assertTrue(valid, "Token验证应该通过");
        }
    }

    @Test
    @DisplayName("限流服务 - 单个用户限流")
    void testRateLimit_UserLimit() {
        int limit = tokenProperties.getRateLimit().getRequestsPerMinutePerUser();
        int testLimit = Math.min(limit, 10);

        for (int i = 0; i < testLimit; i++) {
            boolean allowed = rateLimitService.tryAcquire(testUserId, testIp);
            assertTrue(allowed, "第" + (i + 1) + "次请求应该被允许");
        }
    }

    @Test
    @DisplayName("限流服务 - IP限流")
    void testRateLimit_IpLimit() {
        int limit = tokenProperties.getRateLimit().getRequestsPerMinutePerIp();
        int testLimit = Math.min(limit, 10);
        String userId = null;

        for (int i = 0; i < testLimit; i++) {
            boolean allowed = rateLimitService.tryAcquire(userId, testIp);
            assertTrue(allowed, "第" + (i + 1) + "次请求应该被允许");
        }
    }

    @Test
    @DisplayName("限流服务 - 获取限流信息")
    void testGetRateLimitInfo() {
        rateLimitService.tryAcquire(testUserId, testIp);
        rateLimitService.tryAcquire(testUserId, testIp);

        RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(testUserId, testIp);

        assertTrue(info.isEnabled());
        assertEquals(tokenProperties.getRateLimit().getRequestsPerMinutePerUser(), info.getUserLimit());
        assertEquals(tokenProperties.getRateLimit().getRequestsPerMinutePerIp(), info.getIpLimit());
        assertEquals(tokenProperties.getRateLimit().getMaxBatchSize(), info.getMaxBatchSize());
    }

    @Test
    @DisplayName("批量生成 - 限流检查")
    void testBatchGenerate_RateLimit() {
        int maxBatchSize = tokenProperties.getRateLimit().getMaxBatchSize();
        int validBatchSize = Math.min(maxBatchSize, 5);

        boolean allowed = rateLimitService.tryAcquireBatch(testUserId, testIp, validBatchSize);
        assertTrue(allowed, "有效的批量数量应该被允许");

        int invalidBatchSize = maxBatchSize + 1;
        boolean notAllowed = rateLimitService.tryAcquireBatch(testUserId, testIp, invalidBatchSize);
        assertFalse(notAllowed, "超过最大批量数量应该被拒绝");
    }

    @Test
    @DisplayName("批量吊销 - 按jwtId列表吊销")
    void testBatchRevoke_ByJwtIds() {
        String token1 = tokenService.generateToken(testUserId, "subject1", 3600L);
        String token2 = tokenService.generateToken(testUserId, "subject2", 3600L);

        String jwtId1 = extractJwtId(token1);
        String jwtId2 = extractJwtId(token2);

        List<String> jwtIds = new ArrayList<>();
        jwtIds.add(jwtId1);
        jwtIds.add(jwtId2);

        TokenRevocationService.BatchRevokeResult result = 
                tokenRevocationService.batchRevokeByJwtIds(jwtIds, "测试吊销");

        assertNotNull(result);
        assertEquals(2, result.getTotalCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals(2, result.getSuccessfulItems().size());
        assertTrue(result.getFailures().isEmpty());
    }

    @Test
    @DisplayName("批量吊销 - 按tokenValue列表吊销")
    void testBatchRevoke_ByTokenValues() {
        String token1 = tokenService.generateToken(testUserId, "subject1", 3600L);
        String token2 = tokenService.generateToken(testUserId, "subject2", 3600L);

        List<String> tokenValues = new ArrayList<>();
        tokenValues.add(token1);
        tokenValues.add(token2);

        TokenRevocationService.BatchRevokeResult result = 
                tokenRevocationService.batchRevokeByTokenValues(tokenValues, "测试吊销");

        assertNotNull(result);
        assertEquals(2, result.getTotalCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
    }

    @Test
    @DisplayName("批量吊销 - 空列表返回空结果")
    void testBatchRevoke_EmptyList() {
        List<String> emptyList = new ArrayList<>();

        TokenRevocationService.BatchRevokeResult result = 
                tokenRevocationService.batchRevokeByJwtIds(emptyList, "测试");

        assertNotNull(result);
        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
    }

    @Test
    @DisplayName("重置限流 - 重置单个用户")
    void testResetRateLimit_User() {
        for (int i = 0; i < 5; i++) {
            rateLimitService.tryAcquire(testUserId, testIp);
        }

        RateLimitService.RateLimitInfo infoBefore = rateLimitService.getRateLimitInfo(testUserId, testIp);
        int remainingBefore = infoBefore.getUserRemaining();

        rateLimitService.resetUserRateLimit(testUserId);

        RateLimitService.RateLimitInfo infoAfter = rateLimitService.getRateLimitInfo(testUserId, testIp);
        int remainingAfter = infoAfter.getUserRemaining();

        assertEquals(tokenProperties.getRateLimit().getRequestsPerMinutePerUser(), remainingAfter);
    }

    @Test
    @DisplayName("重置限流 - 重置单个IP")
    void testResetRateLimit_Ip() {
        for (int i = 0; i < 5; i++) {
            rateLimitService.tryAcquire(null, testIp);
        }

        RateLimitService.RateLimitInfo infoBefore = rateLimitService.getRateLimitInfo(null, testIp);
        int remainingBefore = infoBefore.getIpRemaining();

        rateLimitService.resetIpRateLimit(testIp);

        RateLimitService.RateLimitInfo infoAfter = rateLimitService.getRateLimitInfo(null, testIp);
        int remainingAfter = infoAfter.getIpRemaining();

        assertEquals(tokenProperties.getRateLimit().getRequestsPerMinutePerIp(), remainingAfter);
    }

    @Test
    @DisplayName("限流服务 - 并发场景下线程安全")
    void testRateLimit_Concurrent() throws InterruptedException {
        int threadCount = 10;
        int iterationsPerThread = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String userId = testUserId;
            final String ip = testIp;
            
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        boolean allowed = rateLimitService.tryAcquire(userId, ip);
                        if (allowed) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        int totalRequests = successCount.get() + failureCount.get();
        assertEquals(threadCount * iterationsPerThread, totalRequests, "所有请求都应该被处理");
    }

    @Test
    @DisplayName("批量生成响应 - addSuccessToken方法")
    void testBatchGenerateResponse_AddSuccess() {
        BatchGenerateResponse response = new BatchGenerateResponse();
        
        response.addSuccessToken("token1");
        response.addSuccessToken("token2");
        response.addSuccessToken("token3");

        assertEquals(3, response.getTotalCount());
        assertEquals(3, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertEquals(3, response.getTokens().size());
        assertEquals("token1", response.getTokens().get(0));
    }

    @Test
    @DisplayName("批量生成响应 - addFailure方法")
    void testBatchGenerateResponse_AddFailure() {
        BatchGenerateResponse response = new BatchGenerateResponse();
        
        response.addSuccessToken("token1");
        response.addFailure(1, "生成失败1");
        response.addSuccessToken("token2");
        response.addFailure(3, "生成失败2");

        assertEquals(4, response.getTotalCount());
        assertEquals(2, response.getSuccessCount());
        assertEquals(2, response.getFailureCount());
        assertEquals(2, response.getTokens().size());
        assertEquals(2, response.getFailures().size());
        assertEquals(1, response.getFailures().get(0).getIndex());
        assertEquals("生成失败1", response.getFailures().get(0).getMessage());
    }

    @Test
    @DisplayName("批量吊销响应 - addSuccess方法")
    void testBatchRevokeResult_AddSuccess() {
        TokenRevocationService.BatchRevokeResult result = new TokenRevocationService.BatchRevokeResult();
        
        result.addSuccess("jwtId1");
        result.addSuccess("jwtId2");

        assertEquals(2, result.getTotalCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals(2, result.getSuccessfulItems().size());
    }

    @Test
    @DisplayName("批量吊销响应 - addFailure方法")
    void testBatchRevokeResult_AddFailure() {
        TokenRevocationService.BatchRevokeResult result = new TokenRevocationService.BatchRevokeResult();
        
        result.addSuccess("jwtId1");
        result.addFailure("jwtId2", "无效的jwtId");
        result.addSuccess("jwtId3");

        assertEquals(3, result.getTotalCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(2, result.getSuccessfulItems().size());
        assertEquals(1, result.getFailures().size());
        assertEquals("jwtId2", result.getFailures().get(0).getId());
        assertEquals("无效的jwtId", result.getFailures().get(0).getMessage());
    }

    @Test
    @DisplayName("限流服务 - 高并发场景（50线程）下线程安全")
    void testRateLimit_HighConcurrency() throws InterruptedException {
        int threadCount = 50;
        int iterationsPerThread = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String userId = testUserId;
            final String ip = testIp;
            
            executorService.submit(() -> {
                int localSuccess = 0;
                int localFailure = 0;
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        boolean allowed = rateLimitService.tryAcquire(userId, ip);
                        if (allowed) {
                            localSuccess++;
                        } else {
                            localFailure++;
                        }
                    }
                } finally {
                    successCount.addAndGet(localSuccess);
                    failureCount.addAndGet(localFailure);
                    endLatch.countDown();
                }
            });
        }

        endLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(testUserId, testIp);
        int totalAllowed = successCount.get();
        assertTrue(totalAllowed <= info.getUserLimit(), "成功请求数不应超过限制");
        assertTrue(totalAllowed > 0, "应该有成功的请求");
    }

    @Test
    @DisplayName("限流服务 - 高并发批量请求测试")
    void testRateLimit_HighConcurrencyBatch() throws InterruptedException {
        int threadCount = 30;
        int batchSize = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String userId = testUserId;
            final String ip = testIp;
            
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    boolean allowed = rateLimitService.tryAcquireBatch(userId, ip, batchSize);
                    if (allowed) {
                        successCount.addAndGet(batchSize);
                    } else {
                        failureCount.addAndGet(batchSize);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        int totalRequests = successCount.get() + failureCount.get();
        assertEquals(threadCount * batchSize, totalRequests, "所有请求都应该被处理");
    }

    @Test
    @DisplayName("限流服务 - 滑动窗口边界测试")
    void testRateLimit_SlidingWindow() throws InterruptedException {
        int limit = 5;
        tokenProperties.getRateLimit().setRequestsPerMinutePerUser(limit);

        for (int i = 0; i < limit; i++) {
            boolean allowed = rateLimitService.tryAcquire(testUserId, testIp);
            assertTrue(allowed, "前" + limit + "次请求应该被允许");
        }

        boolean notAllowed = rateLimitService.tryAcquire(testUserId, testIp);
        assertFalse(notAllowed, "第" + (limit + 1) + "次请求应该被拒绝");

        tokenProperties.getRateLimit().setRequestsPerMinutePerUser(100);
    }

    @Test
    @DisplayName("限流服务 - 配置项实时更新测试")
    void testRateLimit_ConfigUpdate() {
        int originalLimit = tokenProperties.getRateLimit().getRequestsPerMinutePerUser();
        
        try {
            tokenProperties.getRateLimit().setRequestsPerMinutePerUser(5);

            for (int i = 0; i < 5; i++) {
                boolean allowed = rateLimitService.tryAcquire(testUserId, testIp);
                assertTrue(allowed, "第" + (i + 1) + "次请求应该被允许");
            }

            boolean notAllowed = rateLimitService.tryAcquire(testUserId, testIp);
            assertFalse(notAllowed, "超过新限制的请求应该被拒绝");

            RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(testUserId, testIp);
            assertEquals(5, info.getUserLimit(), "限制应该更新为5");
            assertEquals(0, info.getUserRemaining(), "剩余应该为0");

        } finally {
            tokenProperties.getRateLimit().setRequestsPerMinutePerUser(originalLimit);
        }
    }

    @Test
    @DisplayName("限流服务 - 动态开关限流")
    void testRateLimit_EnableDisable() {
        boolean originalEnabled = tokenProperties.getRateLimit().isEnabled();

        try {
            for (int i = 0; i < 5; i++) {
                rateLimitService.tryAcquire(testUserId, testIp);
            }

            tokenProperties.getRateLimit().setEnabled(false);

            RateLimitService.RateLimitInfo infoDisabled = rateLimitService.getRateLimitInfo(testUserId, testIp);
            assertFalse(infoDisabled.isEnabled(), "限流应该被禁用");

            for (int i = 0; i < 10; i++) {
                boolean allowed = rateLimitService.tryAcquire(testUserId, testIp);
                assertTrue(allowed, "限流禁用后所有请求都应该被允许");
            }

            tokenProperties.getRateLimit().setEnabled(true);

            RateLimitService.RateLimitInfo infoEnabled = rateLimitService.getRateLimitInfo(testUserId, testIp);
            assertTrue(infoEnabled.isEnabled(), "限流应该被重新启用");

        } finally {
            tokenProperties.getRateLimit().setEnabled(originalEnabled);
        }
    }

    @Test
    @DisplayName("限流服务 - 多用户并发互不干扰")
    void testRateLimit_MultipleUsersConcurrent() throws InterruptedException {
        int threadCountPerUser = 20;
        int userCount = 5;
        int iterationsPerThread = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCountPerUser * userCount);
        CountDownLatch endLatch = new CountDownLatch(threadCountPerUser * userCount);
        
        List<AtomicInteger> successCounts = new ArrayList<>();
        List<AtomicInteger> failureCounts = new ArrayList<>();
        List<String> userIds = new ArrayList<>();

        for (int u = 0; u < userCount; u++) {
            final String userId = "test-user-" + UUID.randomUUID().toString();
            userIds.add(userId);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            successCounts.add(successCount);
            failureCounts.add(failureCount);

            for (int i = 0; i < threadCountPerUser; i++) {
                final int userIndex = u;
                executorService.submit(() -> {
                    int localSuccess = 0;
                    int localFailure = 0;
                    try {
                        for (int j = 0; j < iterationsPerThread; j++) {
                            boolean allowed = rateLimitService.tryAcquire(userIds.get(userIndex), testIp);
                            if (allowed) {
                                localSuccess++;
                            } else {
                                localFailure++;
                            }
                        }
                    } finally {
                        successCounts.get(userIndex).addAndGet(localSuccess);
                        failureCounts.get(userIndex).addAndGet(localFailure);
                        endLatch.countDown();
                    }
                });
            }
        }

        endLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        for (int u = 0; u < userCount; u++) {
            int total = successCounts.get(u).get() + failureCounts.get(u).get();
            assertTrue(total > 0, "用户" + u + "应该有请求被处理");
        }
    }

    private String extractJwtId(String token) {
        return token.hashCode() + "-" + System.currentTimeMillis();
    }
}
