package com.example.tokenservice.service;

import com.example.tokenservice.config.JwtKeyManager;
import com.example.tokenservice.dto.TokenLinkNode;
import com.example.tokenservice.model.TokenOperationType;
import com.example.tokenservice.model.TokenStatistics;
import com.example.tokenservice.repository.TokenStatisticsRepository;
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
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "token.statistics.enabled=true",
    "token.statistics.record-invalid-tokens=false"
})
class TokenLinkTraceTest {

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

    @Test
    void renewToken_ShouldRecordParentJwtId() {
        String userId = "link-trace-user-001";

        String oldToken = tokenService.generateToken(userId, "test", 3600L);
        String oldJwtId = jwtKeyManager.extractJwtIdQuietly(oldToken);
        waitForRecords(1);

        String newToken = tokenService.renewToken(oldToken, 3600L, true);
        String newJwtId = jwtKeyManager.extractJwtIdQuietly(newToken);
        waitForRecords(2);

        assertNotEquals(oldJwtId, newJwtId);

        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);
        List<TokenStatistics> records = statisticsService.getUserRecords(userId, start, end);

        Optional<TokenStatistics> renewRecord = records.stream()
                .filter(r -> r.getOperationType() == TokenOperationType.RENEW)
                .findFirst();

        assertTrue(renewRecord.isPresent(), "应该有 RENEW 类型的记录");
        assertEquals(newJwtId, renewRecord.get().getJwtId());
        assertEquals(oldJwtId, renewRecord.get().getParentJwtId(), "续签记录应该记录 parentJwtId");
    }

    @Test
    void getTokenChain_WithSimpleChain_ShouldReturnTree() {
        String userId = "link-trace-user-002";

        String token1 = tokenService.generateToken(userId, "test", 3600L);
        String jwtId1 = jwtKeyManager.extractJwtIdQuietly(token1);
        waitForRecords(1);

        String token2 = tokenService.renewToken(token1, 3600L, true);
        String jwtId2 = jwtKeyManager.extractJwtIdQuietly(token2);
        waitForRecords(2);

        String token3 = tokenService.renewToken(token2, 3600L, true);
        String jwtId3 = jwtKeyManager.extractJwtIdQuietly(token3);
        waitForRecords(3);

        TokenLinkNode chain = statisticsService.getTokenChain(jwtId1);

        assertNotNull(chain, "应该找到根节点");
        assertEquals(jwtId1, chain.getJwtId());
        assertEquals(TokenOperationType.GENERATE, chain.getOperationType());
        assertNull(chain.getParentJwtId());

        assertEquals(1, chain.getChildren().size(), "根节点应该有一个子节点");
        TokenLinkNode child1 = chain.getChildren().get(0);
        assertEquals(jwtId2, child1.getJwtId());
        assertEquals(TokenOperationType.RENEW, child1.getOperationType());
        assertEquals(jwtId1, child1.getParentJwtId());

        assertEquals(1, child1.getChildren().size(), "子节点应该有一个子节点");
        TokenLinkNode child2 = child1.getChildren().get(0);
        assertEquals(jwtId3, child2.getJwtId());
        assertEquals(TokenOperationType.RENEW, child2.getOperationType());
        assertEquals(jwtId2, child2.getParentJwtId());

        assertTrue(child2.getChildren().isEmpty(), "最后一个节点应该没有子节点");
    }

    @Test
    void getTokenChain_WithNonExistentJwtId_ShouldReturnNull() {
        TokenLinkNode chain = statisticsService.getTokenChain("non-existent-jwt-id");
        assertNull(chain, "不存在的 jwtId 应该返回 null");
    }

    @Test
    void getTokenChain_WithMultipleRenews_ShouldBuildCorrectTree() {
        String userId = "link-trace-user-003";

        String tokenA = tokenService.generateToken(userId, "test", 3600L);
        String jwtIdA = jwtKeyManager.extractJwtIdQuietly(tokenA);
        waitForRecords(1);

        String tokenB = tokenService.renewToken(tokenA, 3600L, false);
        String jwtIdB = jwtKeyManager.extractJwtIdQuietly(tokenB);
        waitForRecords(2);

        String tokenC = tokenService.renewToken(tokenA, 3600L, false);
        String jwtIdC = jwtKeyManager.extractJwtIdQuietly(tokenC);
        waitForRecords(3);

        TokenLinkNode chain = statisticsService.getTokenChain(jwtIdA);

        assertNotNull(chain);
        assertEquals(jwtIdA, chain.getJwtId());
        assertEquals(2, chain.getChildren().size(), "根节点应该有两个子节点");

        List<String> childJwtIds = List.of(
                chain.getChildren().get(0).getJwtId(),
                chain.getChildren().get(1).getJwtId()
        );
        assertTrue(childJwtIds.contains(jwtIdB));
        assertTrue(childJwtIds.contains(jwtIdC));
    }

    @Test
    void getTokenChain_StartingFromMiddleNode_ShouldReturnSubtree() {
        String userId = "link-trace-user-004";

        String token1 = tokenService.generateToken(userId, "test", 3600L);
        String jwtId1 = jwtKeyManager.extractJwtIdQuietly(token1);
        waitForRecords(1);

        String token2 = tokenService.renewToken(token1, 3600L, true);
        String jwtId2 = jwtKeyManager.extractJwtIdQuietly(token2);
        waitForRecords(2);

        String token3 = tokenService.renewToken(token2, 3600L, true);
        String jwtId3 = jwtKeyManager.extractJwtIdQuietly(token3);
        waitForRecords(3);

        TokenLinkNode chain = statisticsService.getTokenChain(jwtId2);

        assertNotNull(chain);
        assertEquals(jwtId2, chain.getJwtId());
        assertEquals(TokenOperationType.RENEW, chain.getOperationType());
        assertEquals(jwtId1, chain.getParentJwtId());

        assertEquals(1, chain.getChildren().size());
        assertEquals(jwtId3, chain.getChildren().get(0).getJwtId());
    }

    @Test
    void generateToken_ShouldNotHaveParentJwtId() {
        String userId = "link-trace-user-005";

        String token = tokenService.generateToken(userId, "test", 3600L);
        waitForRecords(1);

        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);
        List<TokenStatistics> records = statisticsService.getUserRecords(userId, start, end);

        assertEquals(1, records.size());
        assertNull(records.get(0).getParentJwtId(), "生成的 Token 不应该有 parentJwtId");
    }

    @Test
    void validateToken_ShouldNotHaveParentJwtId() {
        String userId = "link-trace-user-006";

        String token = tokenService.generateToken(userId, "test", 3600L);
        waitForRecords(1);

        tokenService.validateToken(token);
        waitForRecords(2);

        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);
        List<TokenStatistics> records = statisticsService.getUserRecords(userId, start, end);

        Optional<TokenStatistics> validateRecord = records.stream()
                .filter(r -> r.getOperationType() == TokenOperationType.VALIDATE)
                .findFirst();

        assertTrue(validateRecord.isPresent());
        assertNull(validateRecord.get().getParentJwtId(), "验证操作不应该有 parentJwtId");
    }

    @Test
    void invalidateToken_ShouldNotHaveParentJwtId() {
        String userId = "link-trace-user-007";

        String token = tokenService.generateToken(userId, "test", 3600L);
        waitForRecords(1);

        tokenService.invalidateToken(token);
        waitForRecords(2);

        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);
        List<TokenStatistics> records = statisticsService.getUserRecords(userId, start, end);

        Optional<TokenStatistics> invalidateRecord = records.stream()
                .filter(r -> r.getOperationType() == TokenOperationType.INVALIDATE)
                .findFirst();

        assertTrue(invalidateRecord.isPresent());
        assertNull(invalidateRecord.get().getParentJwtId(), "作废操作不应该有 parentJwtId");
    }

    @Test
    void getTokenChain_ShouldIncludeAllFields() {
        String userId = "link-trace-user-008";

        String token = tokenService.generateToken(userId, "test", 3600L);
        String jwtId = jwtKeyManager.extractJwtIdQuietly(token);
        waitForRecords(1);

        TokenLinkNode chain = statisticsService.getTokenChain(jwtId);

        assertNotNull(chain);
        assertEquals(jwtId, chain.getJwtId());
        assertEquals(userId, chain.getUserId());
        assertEquals(TokenOperationType.GENERATE, chain.getOperationType());
        assertNull(chain.getParentJwtId());
        assertTrue(chain.isSuccess());
        assertNull(chain.getFailureReason());
        assertNotNull(chain.getOperationTime());
        assertTrue(chain.getChildren().isEmpty());
    }
}
