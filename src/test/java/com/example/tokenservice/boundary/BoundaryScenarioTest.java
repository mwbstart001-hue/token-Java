package com.example.tokenservice.boundary;

import com.example.tokenservice.config.JwtKeyManager;
import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.dto.ApiResponse;
import com.example.tokenservice.monitor.TokenPerformanceMonitor;
import com.example.tokenservice.revocation.TokenRevocationService;
import com.example.tokenservice.revocation.TokenRevocationStore;
import com.example.tokenservice.strategy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("边界场景测试")
class BoundaryScenarioTest {

    private TokenProperties tokenProperties;
    private JwtKeyManager jwtKeyManager;

    @BeforeEach
    void setUp() {
        tokenProperties = new TokenProperties();
        tokenProperties.setSecret("test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm");
        tokenProperties.setDefaultExpireSeconds(3600L);
        tokenProperties.setMaxExpireSeconds(86400L * 30);
        tokenProperties.setAlgorithm("RS256");

        jwtKeyManager = new JwtKeyManager(tokenProperties);
        jwtKeyManager.init();
    }

    @Nested
    @DisplayName("空参数测试")
    class NullParameterTests {

        @Test
        @DisplayName("RS256TokenGenerator - null userId")
        void testRS256Generator_NullUserId() {
            RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
            
            TokenGenerationResult result = generator.generate(
                    null, 
                    "test-subject", 
                    LocalDateTime.now(), 
                    LocalDateTime.now().plusHours(1)
            );
            
            assertNotNull(result);
            assertNotNull(result.getTokenValue());
            assertNotNull(result.getJwtId());
        }

        @Test
        @DisplayName("RS256TokenGenerator - null subject")
        void testRS256Generator_NullSubject() {
            RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
            
            TokenGenerationResult result = generator.generate(
                    "user-001", 
                    null, 
                    LocalDateTime.now(), 
                    LocalDateTime.now().plusHours(1)
            );
            
            assertNotNull(result);
            assertNotNull(result.getTokenValue());
            assertNotNull(result.getJwtId());
        }

        @Test
        @DisplayName("RS256TokenGenerator - null times should throw")
        void testRS256Generator_NullTimes() {
            RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
            
            assertThrows(NullPointerException.class, () -> {
                generator.generate(
                        "user-001", 
                        "test-subject", 
                        null, 
                        null
                );
            });
        }

        @Test
        @DisplayName("HS256TokenGenerator - null userId")
        void testHS256Generator_NullUserId() {
            HS256TokenGenerator generator = new HS256TokenGenerator(jwtKeyManager);
            
            TokenGenerationResult result = generator.generate(
                    null, 
                    "test-subject", 
                    LocalDateTime.now(), 
                    LocalDateTime.now().plusHours(1)
            );
            
            assertNotNull(result);
            assertNotNull(result.getTokenValue());
            assertNotNull(result.getJwtId());
        }

        @Test
        @DisplayName("SimpleTokenGenerator - null userId")
        void testSimpleGenerator_NullUserId() {
            SimpleTokenGenerator generator = new SimpleTokenGenerator(tokenProperties);
            
            TokenGenerationResult result = generator.generate(
                    null, 
                    "test-subject", 
                    LocalDateTime.now(), 
                    LocalDateTime.now().plusHours(1)
            );
            
            assertNotNull(result);
            assertNotNull(result.getTokenValue());
            assertNotNull(result.getJwtId());
            assertTrue(result.getTokenValue().startsWith("TOKEN-"));
        }

        @Test
        @DisplayName("TokenValidator - null token")
        void testValidator_NullToken() {
            RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
            
            TokenValidator.ValidationResult result = validator.validate(null);
            
            assertNotNull(result);
            assertFalse(result.isValid());
            assertEquals(TokenValidator.ValidationStatus.INVALID, result.getStatus());
        }

        @Test
        @DisplayName("TokenValidator - empty token")
        void testValidator_EmptyToken() {
            RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
            
            TokenValidator.ValidationResult result = validator.validate("");
            
            assertNotNull(result);
            assertFalse(result.isValid());
            assertEquals(TokenValidator.ValidationStatus.INVALID, result.getStatus());
        }

        @Test
        @DisplayName("TokenValidator - whitespace token")
        void testValidator_WhitespaceToken() {
            RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
            
            TokenValidator.ValidationResult result = validator.validate("   ");
            
            assertNotNull(result);
            assertFalse(result.isValid());
            assertEquals(TokenValidator.ValidationStatus.INVALID, result.getStatus());
        }

        @Test
        @DisplayName("TokenRevocationService - null jwtId")
        void testRevocationService_NullJwtId() {
            TokenRevocationStore mockStore = mock(TokenRevocationStore.class);
            TokenRevocationService service = new TokenRevocationService(mockStore);
            
            service.revokeByJwtId(null, "test-reason");
            
            verify(mockStore, never()).revokeByJwtId(anyString(), anyString());
        }

        @Test
        @DisplayName("TokenRevocationService - empty jwtId")
        void testRevocationService_EmptyJwtId() {
            TokenRevocationStore mockStore = mock(TokenRevocationStore.class);
            TokenRevocationService service = new TokenRevocationService(mockStore);
            
            service.revokeByJwtId("", "test-reason");
            
            verify(mockStore, never()).revokeByJwtId(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("超长参数测试")
    class LongParameterTests {

        private String generateLongString(int length) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append((char) ('a' + (i % 26)));
            }
            return sb.toString();
        }

        @Test
        @DisplayName("超长 userId (10000 chars)")
        void testVeryLongUserId() {
            RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
            String longUserId = generateLongString(10000);
            
            TokenGenerationResult result = generator.generate(
                    longUserId, 
                    "test-subject", 
                    LocalDateTime.now(), 
                    LocalDateTime.now().plusHours(1)
            );
            
            assertNotNull(result);
            assertNotNull(result.getTokenValue());
            assertNotNull(result.getJwtId());
            
            RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
            TokenValidator.ValidationResult validation = validator.validate(result.getTokenValue());
            
            assertTrue(validation.isValid());
            assertEquals(longUserId, validation.getClaims().get("userId"));
        }

        @Test
        @DisplayName("超长 subject (10000 chars)")
        void testVeryLongSubject() {
            HS256TokenGenerator generator = new HS256TokenGenerator(jwtKeyManager);
            String longSubject = generateLongString(10000);
            
            TokenGenerationResult result = generator.generate(
                    "user-001", 
                    longSubject, 
                    LocalDateTime.now(), 
                    LocalDateTime.now().plusHours(1)
            );
            
            assertNotNull(result);
            assertNotNull(result.getTokenValue());
            assertNotNull(result.getJwtId());
        }

        @Test
        @DisplayName("超长 tokenValue (10000 chars)")
        void testVeryLongTokenValue() {
            RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
            String longToken = generateLongString(10000);
            
            TokenValidator.ValidationResult result = validator.validate(longToken);
            
            assertNotNull(result);
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("超长吊销原因 (10000 chars)")
        void testVeryLongRevocationReason() {
            TokenRevocationStore mockStore = mock(TokenRevocationStore.class);
            TokenRevocationService service = new TokenRevocationService(mockStore);
            
            String longReason = generateLongString(10000);
            service.revokeByJwtId("test-jwt-id", longReason);
            
            verify(mockStore).revokeByJwtId("test-jwt-id", longReason);
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {

        private static final int THREAD_COUNT = 50;
        private static final int ITERATIONS_PER_THREAD = 100;

        @Test
        @DisplayName("并发生成 Token (RS256)")
        void testConcurrentTokenGeneration_RS256() throws InterruptedException {
            RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
            
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<String> generatedTokens = new ArrayList<>();
            List<String> generatedJwtIds = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        barrier.await();
                        for (int j = 0; j < ITERATIONS_PER_THREAD; j++) {
                            TokenGenerationResult result = generator.generate(
                                    "user-" + threadIndex,
                                    "concurrent-test",
                                    LocalDateTime.now(),
                                    LocalDateTime.now().plusHours(1)
                            );
                            synchronized (generatedTokens) {
                                generatedTokens.add(result.getTokenValue());
                                generatedJwtIds.add(result.getJwtId());
                            }
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(0, errorCount.get(), "不应该有错误");
            assertEquals(THREAD_COUNT * ITERATIONS_PER_THREAD, successCount.get(), "所有生成应该成功");
            
            long uniqueTokenCount = generatedTokens.stream().distinct().count();
            long uniqueJwtIdCount = generatedJwtIds.stream().distinct().count();
            
            assertEquals(generatedTokens.size(), uniqueTokenCount, "所有 Token 应该唯一");
            assertEquals(generatedJwtIds.size(), uniqueJwtIdCount, "所有 JwtId 应该唯一");
        }

        @Test
        @DisplayName("并发生成和验证 Token")
        void testConcurrentGenerateAndValidate() throws InterruptedException {
            RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
            RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            
            AtomicInteger generateSuccess = new AtomicInteger(0);
            AtomicInteger validateSuccess = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            AtomicReference<String> sharedToken = new AtomicReference<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        if (threadIndex % 2 == 0) {
                            TokenGenerationResult result = generator.generate(
                                    "user-" + threadIndex,
                                    "concurrent-test",
                                    LocalDateTime.now(),
                                    LocalDateTime.now().plusHours(1)
                            );
                            sharedToken.set(result.getTokenValue());
                            generateSuccess.incrementAndGet();
                        } else {
                            String token = sharedToken.get();
                            if (token != null) {
                                TokenValidator.ValidationResult result = validator.validate(token);
                                if (result.isValid()) {
                                    validateSuccess.incrementAndGet();
                                }
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(0, errorCount.get(), "不应该有错误");
            assertTrue(generateSuccess.get() > 0, "应该有成功的生成");
        }

        @Test
        @DisplayName("并发吊销和检查黑名单")
        void testConcurrentRevokeAndCheck() throws InterruptedException {
            TokenRevocationStore mockStore = mock(TokenRevocationStore.class);
            TokenRevocationService service = new TokenRevocationService(mockStore);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            
            AtomicInteger errorCount = new AtomicInteger(0);

            when(mockStore.isRevoked(anyString(), anyString())).thenReturn(false);

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        if (threadIndex % 2 == 0) {
                            service.revokeByJwtId("jwt-id-" + threadIndex, "test-reason");
                        } else {
                            service.isRevoked("jwt-id-" + (threadIndex - 1), "token-value");
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(0, errorCount.get(), "并发操作不应该有错误");
        }

        @Test
        @DisplayName("所有策略并发生成测试")
        void testAllStrategiesConcurrentGeneration() throws InterruptedException {
            RS256TokenGenerator rs256Generator = new RS256TokenGenerator(jwtKeyManager);
            HS256TokenGenerator hs256Generator = new HS256TokenGenerator(jwtKeyManager);
            SimpleTokenGenerator simpleGenerator = new SimpleTokenGenerator(tokenProperties);
            
            int strategyCount = 3;
            int totalThreads = 30;
            int iterations = 50;
            
            ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
            CountDownLatch latch = new CountDownLatch(totalThreads);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            List<String> allTokens = new ArrayList<>();
            List<String> allJwtIds = new ArrayList<>();

            for (int i = 0; i < totalThreads; i++) {
                final int strategyIndex = i % strategyCount;
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        TokenGenerator generator;
                        if (strategyIndex == 0) {
                            generator = rs256Generator;
                        } else if (strategyIndex == 1) {
                            generator = hs256Generator;
                        } else {
                            generator = simpleGenerator;
                        }
                        
                        for (int j = 0; j < iterations; j++) {
                            TokenGenerationResult result = generator.generate(
                                    "user-" + threadIndex + "-" + j,
                                    "strategy-test",
                                    LocalDateTime.now(),
                                    LocalDateTime.now().plusHours(1)
                            );
                            synchronized (allTokens) {
                                allTokens.add(result.getTokenValue());
                                allJwtIds.add(result.getJwtId());
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(0, errorCount.get(), "所有策略并发生成不应该有错误");
            assertEquals(totalThreads * iterations, allTokens.size(), "应该生成正确数量的 Token");
            
            long uniqueTokens = allTokens.stream().distinct().count();
            long uniqueJwtIds = allJwtIds.stream().distinct().count();
            
            assertEquals(allTokens.size(), uniqueTokens, "所有 Token 应该唯一");
            assertEquals(allJwtIds.size(), uniqueJwtIds, "所有 JwtId 应该唯一");
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryValueTests {

        @Test
        @DisplayName("过期时间为过去时间")
        void testExpiredTokenGeneration() {
            RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
            
            TokenGenerationResult result = generator.generate(
                    "user-001",
                    "test-expired",
                    LocalDateTime.now().minusHours(2),
                    LocalDateTime.now().minusHours(1)
            );
            
            assertNotNull(result);
            assertNotNull(result.getTokenValue());
            
            RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
            TokenValidator.ValidationResult validation = validator.validate(result.getTokenValue());
            
            assertFalse(validation.isValid());
            assertEquals(TokenValidator.ValidationStatus.EXPIRED, validation.getStatus());
        }

        @Test
        @DisplayName("过期时间为当前时间")
        void testExactlyExpiredToken() {
            RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
            
            LocalDateTime now = LocalDateTime.now();
            TokenGenerationResult result = generator.generate(
                    "user-001",
                    "test-exact",
                    now.minusHours(1),
                    now
            );
            
            assertNotNull(result);
            assertNotNull(result.getTokenValue());
        }

        @Test
        @DisplayName("极短过期时间（1秒）")
        void testVeryShortExpiration() throws InterruptedException {
            RS256TokenGenerator generator = new RS256TokenGenerator(jwtKeyManager);
            
            LocalDateTime now = LocalDateTime.now();
            TokenGenerationResult result = generator.generate(
                    "user-001",
                    "test-short",
                    now,
                    now.plusSeconds(1)
            );
            
            assertNotNull(result);
            
            RS256TokenValidator validator = new RS256TokenValidator(jwtKeyManager);
            
            TokenValidator.ValidationResult beforeExpire = validator.validate(result.getTokenValue());
            assertTrue(beforeExpire.isValid() || beforeExpire.getStatus() == TokenValidator.ValidationStatus.EXPIRED);
            
            Thread.sleep(2000);
            
            TokenValidator.ValidationResult afterExpire = validator.validate(result.getTokenValue());
            assertFalse(afterExpire.isValid());
        }

        @Test
        @DisplayName("Simple Token 格式边界")
        void testSimpleTokenFormat() {
            SimpleTokenGenerator generator = new SimpleTokenGenerator(tokenProperties);
            SimpleTokenValidator validator = new SimpleTokenValidator(tokenProperties);
            
            TokenGenerationResult result = generator.generate(
                    "user-001",
                    "simple-test",
                    LocalDateTime.now(),
                    LocalDateTime.now().plusHours(1)
            );
            
            assertTrue(result.getTokenValue().startsWith("TOKEN-"));
            
            TokenValidator.ValidationResult validation = validator.validate(result.getTokenValue());
            assertTrue(validation.isValid());
            
            TokenValidator.ValidationResult wrongPrefix = validator.validate("INVALID-" + result.getTokenValue().substring(6));
            assertFalse(wrongPrefix.isValid());
            assertEquals(TokenValidator.ValidationStatus.MALFORMED, wrongPrefix.getStatus());
        }

        @Test
        @DisplayName("不同策略生成的 Token 跨验证")
        void testCrossStrategyValidation() {
            RS256TokenGenerator rs256Generator = new RS256TokenGenerator(jwtKeyManager);
            HS256TokenValidator hs256Validator = new HS256TokenValidator(jwtKeyManager);
            
            TokenGenerationResult rs256Result = rs256Generator.generate(
                    "user-001",
                    "cross-test",
                    LocalDateTime.now(),
                    LocalDateTime.now().plusHours(1)
            );
            
            TokenValidator.ValidationResult crossValidation = hs256Validator.validate(rs256Result.getTokenValue());
            
            assertFalse(crossValidation.isValid());
        }
    }
}
