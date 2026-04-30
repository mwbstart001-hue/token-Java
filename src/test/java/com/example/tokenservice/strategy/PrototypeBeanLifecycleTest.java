package com.example.tokenservice.strategy;

import com.example.tokenservice.TokenServiceApplication;
import com.example.tokenservice.config.JwtKeyManager;
import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.monitor.TokenPerformanceMonitor;
import com.example.tokenservice.revocation.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = TokenServiceApplication.class)
class PrototypeBeanLifecycleTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TokenProperties tokenProperties;

    @Autowired
    private JwtKeyManager jwtKeyManager;

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
    void testPrototypeBean_EachGetReturnsNewInstance() {
        TokenGenerator gen1 = applicationContext.getBean("rs256TokenGenerator", TokenGenerator.class);
        TokenGenerator gen2 = applicationContext.getBean("rs256TokenGenerator", TokenGenerator.class);
        TokenGenerator gen3 = applicationContext.getBean("rs256TokenGenerator", TokenGenerator.class);

        assertNotSame(gen1, gen2, "原型 Bean 每次获取都应该是新实例");
        assertNotSame(gen1, gen3, "原型 Bean 每次获取都应该是新实例");
        assertNotSame(gen2, gen3, "原型 Bean 每次获取都应该是新实例");
    }

    @Test
    void testPrototypeBean_DifferentBeansHaveDifferentIdentities() {
        TokenGenerator rs256Gen = applicationContext.getBean("rs256TokenGenerator", TokenGenerator.class);
        TokenGenerator jwtGen = applicationContext.getBean("jwtTokenGenerator", TokenGenerator.class);

        assertNotNull(rs256Gen);
        assertNotNull(jwtGen);
        assertNotNull(rs256Gen.getAlgorithm());
        assertNotNull(jwtGen.getAlgorithm());
        assertNotSame(rs256Gen, jwtGen, "不同 Bean 名称的生成器应该是不同的实例");
    }

    @Test
    void testPrototypeBean_ConcurrentAccess_ThreadSafe() throws InterruptedException {
        int threadCount = 50;
        int iterationsPerThread = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        Set<Object> instances = new HashSet<>();
        Object lock = new Object();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        TokenGenerator gen = applicationContext.getBean("rs256TokenGenerator", TokenGenerator.class);
                        
                        TokenGenerationResult result = gen.generate(
                            "user-" + Thread.currentThread().getId(),
                            "test",
                            LocalDateTime.now(),
                            LocalDateTime.now().plusHours(1)
                        );
                        
                        synchronized (lock) {
                            instances.add(gen);
                        }
                        
                        assertNotNull(result);
                        assertNotNull(result.getTokenValue());
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * iterationsPerThread, successCount.get(), "所有操作应该成功");
        
        assertTrue(instances.size() > 1, "原型 Bean 应该创建多个实例");
        assertTrue(instances.size() <= threadCount * iterationsPerThread, "实例数量不应该超过调用次数");
    }

    @Test
    void testTokenStrategySelector_GetNewGenerator_ReturnsNewInstance() {
        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        TokenGenerator gen1 = selector.getNewGenerator();
        TokenGenerator gen2 = selector.getNewGenerator();
        TokenGenerator gen3 = selector.getNewGenerator();

        assertNotSame(gen1, gen2, "getNewGenerator 每次都应该返回新实例");
        assertNotSame(gen1, gen3, "getNewGenerator 每次都应该返回新实例");
        assertNotSame(gen2, gen3, "getNewGenerator 每次都应该返回新实例");
    }

    @Test
    void testTokenStrategySelector_GetNewValidator_ReturnsNewInstance() {
        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        TokenValidator val1 = selector.getNewValidator();
        TokenValidator val2 = selector.getNewValidator();
        TokenValidator val3 = selector.getNewValidator();

        assertNotSame(val1, val2, "getNewValidator 每次都应该返回新实例");
        assertNotSame(val1, val3, "getNewValidator 每次都应该返回新实例");
        assertNotSame(val2, val3, "getNewValidator 每次都应该返回新实例");
    }

    @Test
    void testTokenStrategySelector_Generate_UsesPrototypeBean() {
        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        TokenGenerator gen1 = selector.getSelectedGenerator();
        TokenGenerator gen2 = selector.getSelectedGenerator();

        assertNotSame(gen1, gen2, "getSelectedGenerator 每次都应该返回新实例");
    }

    @Test
    void testTokenStrategySelector_SwitchStrategy_GetNewBean() {
        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        TokenGenerator gen1 = selector.getNewGenerator();
        String algo1 = selector.getCurrentAlgorithm();

        boolean switched = selector.switchStrategy("JWT", "RS256");
        assertTrue(switched, "策略切换应该成功");

        TokenGenerator gen2 = selector.getNewGenerator();

        assertNotSame(gen1, gen2, "策略切换后获取的应该是新实例");
    }

    @Test
    void testPrototypeBean_GenerateMultipleTokens_Independent() {
        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        List<TokenGenerationResult> results = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            TokenGenerationResult result = selector.generate(
                "user-" + i,
                "test-" + i,
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1)
            );
            results.add(result);
        }

        Set<String> tokenValues = new HashSet<>();
        Set<String> jwtIds = new HashSet<>();

        for (TokenGenerationResult result : results) {
            tokenValues.add(result.getTokenValue());
            jwtIds.add(result.getJwtId());
        }

        assertEquals(10, tokenValues.size(), "每个 Token 应该是唯一的");
        assertEquals(10, jwtIds.size(), "每个 JwtId 应该是唯一的");
    }

    @Test
    void testPrototypeBean_ConcurrentGenerate_ThreadSafe() throws InterruptedException {
        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        int threadCount = 30;
        int tokensPerThread = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        Set<String> tokenValues = new HashSet<>();
        Set<String> jwtIds = new HashSet<>();
        Object lock = new Object();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < tokensPerThread; j++) {
                        TokenGenerationResult result = selector.generate(
                            "thread-" + threadNum + "-user-" + j,
                            "test",
                            LocalDateTime.now(),
                            LocalDateTime.now().plusHours(1)
                        );

                        synchronized (lock) {
                            tokenValues.add(result.getTokenValue());
                            jwtIds.add(result.getJwtId());
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

        assertEquals(threadCount * tokensPerThread, successCount.get(), "所有生成操作应该成功");
        assertEquals(threadCount * tokensPerThread, tokenValues.size(), "并发生成的 Token 应该都是唯一的");
        assertEquals(threadCount * tokensPerThread, jwtIds.size(), "并发生成的 JwtId 应该都是唯一的");
    }

    @Test
    void testPrototypeBean_GetGeneratorBeanName_BasedOnConfig() {
        tokenProperties.getStrategy().setType("JWT");
        tokenProperties.setAlgorithm("RS256");

        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        TokenGenerator gen = selector.getNewGenerator();
        assertNotNull(gen);
        assertEquals("RS256", gen.getAlgorithm());
    }

    @Test
    void testPrototypeBean_GetSimpleGenerator() {
        tokenProperties.getStrategy().setType("SIMPLE");

        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        TokenGenerator gen = selector.getNewGenerator();
        assertNotNull(gen);
        assertEquals("SIMPLE", gen.getAlgorithm());
    }

    @Test
    void testPrototypeBean_SwitchToSimpleStrategy() {
        tokenProperties.getStrategy().setType("JWT");
        tokenProperties.setAlgorithm("RS256");

        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        TokenGenerator gen1 = selector.getNewGenerator();
        assertEquals("RS256", gen1.getAlgorithm());

        boolean switched = selector.switchStrategy("SIMPLE", null);
        assertTrue(switched);

        TokenGenerator gen2 = selector.getNewGenerator();
        assertEquals("SIMPLE", gen2.getAlgorithm());

        assertNotSame(gen1, gen2, "策略切换后应该获取不同的 Bean 实例");
    }

    @Test
    void testPrototypeBean_Validate_UsesNewInstance() {
        TokenStrategySelector selector = new TokenStrategySelector(
            applicationContext, tokenProperties, revocationService, performanceMonitor
        );
        selector.init();

        TokenGenerationResult result = selector.generate(
            "test-user",
            "test",
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(1)
        );

        TokenValidator.ValidationResult val1 = selector.validate(result.getTokenValue());
        TokenValidator.ValidationResult val2 = selector.validate(result.getTokenValue());

        assertTrue(val1.isValid());
        assertTrue(val2.isValid());
    }
}
