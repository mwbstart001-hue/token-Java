package com.example.tokenservice.strategy;

import com.example.tokenservice.TokenServiceApplication;
import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.monitor.TokenPerformanceMonitor;
import com.example.tokenservice.revocation.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = TokenServiceApplication.class)
class TokenStrategySelectorRefactorTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TokenStrategyFactory strategyFactory;

    @Autowired
    private TokenProperties tokenProperties;

    private TokenPerformanceMonitor performanceMonitor;
    private TokenRevocationService revocationService;

    @BeforeEach
    void setUp() {
        performanceMonitor = mock(TokenPerformanceMonitor.class);
        revocationService = mock(TokenRevocationService.class);
        tokenProperties.getStrategy().setType("JWT");
        tokenProperties.setAlgorithm("RS256");
    }

    @Test
    void test_100TimesCall_Creates100DifferentInstances() {
        Set<Object> instances = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            TokenGenerator generator = strategyFactory.getGenerator("JWT", "RS256");
            instances.add(generator);
        }

        assertEquals(100, instances.size(), "100次调用应该创建100个不同的实例");
    }

    @Test
    void test_StrategySwitch_GetsNewStrategyInstance() {
        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, strategyFactory, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        TokenGenerator gen1 = selector.getNewGenerator();
        assertEquals("RS256", gen1.getAlgorithm());

        boolean switched = selector.switchStrategy("SIMPLE", null);
        assertTrue(switched);

        TokenGenerator gen2 = selector.getNewGenerator();
        assertEquals("SIMPLE", gen2.getAlgorithm());

        assertNotSame(gen1, gen2, "策略切换后应该获取不同的实例");
        assertNotEquals(gen1.getClass(), gen2.getClass(), "策略切换后应该是不同类型的实例");
    }

    @Test
    void test_MultipleUsersConcurrent_InstancesIndependent() throws InterruptedException {
        int userCount = 10;
        int callsPerUser = 20;
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);

        Set<Object> allInstances = new HashSet<>();
        Object lock = new Object();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < userCount; i++) {
            final int userId = i;
            
            executor.submit(() -> {
                try {
                    Set<Object> userInstances = new HashSet<>();
                    
                    for (int j = 0; j < callsPerUser; j++) {
                        TokenGenerator generator = strategyFactory.getGenerator("JWT", "RS256");
                        
                        TokenGenerationResult result = generator.generate(
                            "user-" + userId,
                            "test",
                            LocalDateTime.now(),
                            LocalDateTime.now().plusHours(1)
                        );

                        userInstances.add(generator);
                        assertNotNull(result);
                        assertNotNull(result.getTokenValue());
                    }

                    synchronized (lock) {
                        allInstances.addAll(userInstances);
                    }

                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(userCount, successCount.get(), "所有用户的操作应该成功");
        assertTrue(allInstances.size() > userCount, "不同用户应该有不同的实例");
        assertTrue(allInstances.size() <= userCount * callsPerUser, "总实例数不应超过总调用数");
    }

    @Test
    void test_TokenStrategyFactory_LookupMethods() {
        TokenGenerator simpleGen = strategyFactory.getSimpleGenerator();
        TokenGenerator rs256Gen = strategyFactory.getRs256Generator();
        TokenGenerator hs256Gen = strategyFactory.getHs256Generator();
        TokenGenerator jwtGen = strategyFactory.getJwtGenerator();

        assertEquals("SIMPLE", simpleGen.getAlgorithm());
        assertEquals("RS256", rs256Gen.getAlgorithm());
        assertEquals("HS256", hs256Gen.getAlgorithm());

        TokenGenerator simpleGen2 = strategyFactory.getSimpleGenerator();
        TokenGenerator rs256Gen2 = strategyFactory.getRs256Generator();

        assertNotSame(simpleGen, simpleGen2, "@Lookup 每次应该返回新实例");
        assertNotSame(rs256Gen, rs256Gen2, "@Lookup 每次应该返回新实例");
    }

    @Test
    void test_StrategyTypeEnum_BeanNameMapping() {
        assertEquals("simpleTokenGenerator", 
            TokenStrategyFactory.StrategyType.SIMPLE.getGeneratorBeanName());
        assertEquals("simpleTokenValidator", 
            TokenStrategyFactory.StrategyType.SIMPLE.getValidatorBeanName());

        assertEquals("rs256TokenGenerator", 
            TokenStrategyFactory.StrategyType.RS256.getGeneratorBeanName());
        assertEquals("rs256TokenValidator", 
            TokenStrategyFactory.StrategyType.RS256.getValidatorBeanName());

        assertEquals("hs256TokenGenerator", 
            TokenStrategyFactory.StrategyType.HS256.getGeneratorBeanName());
        assertEquals("hs256TokenValidator", 
            TokenStrategyFactory.StrategyType.HS256.getValidatorBeanName());

        assertEquals("jwtTokenGenerator", 
            TokenStrategyFactory.StrategyType.JWT.getGeneratorBeanName());
        assertEquals("jwtTokenValidator", 
            TokenStrategyFactory.StrategyType.JWT.getValidatorBeanName());

        assertEquals(4, TokenStrategyFactory.StrategyType.values().length);
    }

    @Test
    void test_StrategySwitch_DuringConcurrentRequests_ThreadSafe() throws InterruptedException {
        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, strategyFactory, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        int threadCount = 30;
        int iterationsPerThread = 20;
        int switchCount = 5;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount + 1);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger switchSuccessCount = new AtomicInteger(0);
        AtomicBoolean running = new AtomicBoolean(true);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < iterationsPerThread && running.get(); j++) {
                        try {
                            TokenGenerator generator = selector.getNewGenerator();
                            TokenGenerationResult result = generator.generate(
                                "user-" + threadId,
                                "test",
                                LocalDateTime.now(),
                                LocalDateTime.now().plusHours(1)
                            );
                            
                            assertNotNull(result);
                            assertNotNull(result.getTokenValue());
                            successCount.incrementAndGet();
                            
                            Thread.yield();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        executor.submit(() -> {
            try {
                startLatch.await();
                
                for (int i = 0; i < switchCount; i++) {
                    String strategy = (i % 2 == 0) ? "JWT" : "SIMPLE";
                    String algorithm = (i % 2 == 0) ? "RS256" : null;
                    
                    boolean switched = selector.switchStrategy(strategy, algorithm);
                    if (switched) {
                        switchSuccessCount.incrementAndGet();
                    }
                    
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                running.set(false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertTrue(successCount.get() > 0, "应该有成功的请求");
        assertEquals(0, failureCount.get(), "不应该有失败的请求");
        assertTrue(switchSuccessCount.get() > 0, "策略切换应该成功");
    }

    @Test
    void test_StrategyFactory_GetGenerator_AllTypes() {
        TokenGenerator simpleGen = strategyFactory.getGenerator("SIMPLE", null);
        TokenGenerator rs256Gen = strategyFactory.getGenerator("JWT", "RS256");
        TokenGenerator hs256Gen = strategyFactory.getGenerator("JWT", "HS256");
        TokenGenerator jwtGen = strategyFactory.getGenerator("JWT", "OTHER");

        assertEquals("SIMPLE", simpleGen.getAlgorithm());
        assertEquals("RS256", rs256Gen.getAlgorithm());
        assertEquals("HS256", hs256Gen.getAlgorithm());

        assertNotSame(simpleGen, rs256Gen);
        assertNotSame(rs256Gen, hs256Gen);
    }

    @Test
    void test_StrategyFactory_GetValidator_AllTypes() {
        TokenValidator simpleVal = strategyFactory.getValidator("SIMPLE", null);
        TokenValidator rs256Val = strategyFactory.getValidator("JWT", "RS256");
        TokenValidator hs256Val = strategyFactory.getValidator("JWT", "HS256");
        TokenValidator jwtVal = strategyFactory.getValidator("JWT", "OTHER");

        assertNotNull(simpleVal);
        assertNotNull(rs256Val);
        assertNotNull(hs256Val);
        assertNotNull(jwtVal);

        assertNotSame(simpleVal, rs256Val);
        assertNotSame(rs256Val, hs256Val);
    }

    @Test
    void test_StrategySelector_GetStrategyFactory() {
        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, strategyFactory, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        TokenStrategyFactory factory = selector.getStrategyFactory();
        assertNotNull(factory);
        assertSame(strategyFactory, factory);
    }

    @Test
    void test_ConcurrentLookupMethods_ThreadSafe() throws InterruptedException {
        int threadCount = 50;
        int iterationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        Set<Object> generatorInstances = new HashSet<>();
        Set<Object> validatorInstances = new HashSet<>();
        Object lock = new Object();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        TokenGenerator gen = strategyFactory.getRs256Generator();
                        TokenValidator val = strategyFactory.getRs256Validator();
                        
                        synchronized (lock) {
                            generatorInstances.add(gen);
                            validatorInstances.add(val);
                        }
                        
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * iterationsPerThread, successCount.get());
        assertTrue(generatorInstances.size() > 1, "应该创建多个 Generator 实例");
        assertTrue(validatorInstances.size() > 1, "应该创建多个 Validator 实例");
        assertEquals(generatorInstances.size(), successCount.get(), "每次调用都应该是新实例");
        assertEquals(validatorInstances.size(), successCount.get(), "每次调用都应该是新实例");
    }
}
