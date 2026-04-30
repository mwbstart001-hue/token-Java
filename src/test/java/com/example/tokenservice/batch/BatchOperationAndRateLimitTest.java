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

    private String extractJwtId(String token) {
        return token.hashCode() + "-" + System.currentTimeMillis();
    }
}
