package com.example.tokenservice.revocation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TokenRevocationServiceTest {

    private TokenRevocationStore revocationStore;
    private TokenRevocationService revocationService;

    @BeforeEach
    void setUp() {
        revocationStore = new InMemoryTokenRevocationStore();
        revocationService = new TokenRevocationService(revocationStore);
    }

    @Test
    void testRevokeByJwtId_ShouldAddToBlacklist() {
        String jwtId = UUID.randomUUID().toString();
        String reason = "用户登出";
        
        revocationService.revokeByJwtId(jwtId, reason);
        
        assertTrue(revocationService.isRevoked(jwtId, null));
    }

    @Test
    void testRevokeByJwtId_WithNullJwtId_ShouldNotThrow() {
        assertDoesNotThrow(() -> revocationService.revokeByJwtId(null, "测试"));
    }

    @Test
    void testRevokeByJwtId_WithEmptyJwtId_ShouldNotAddToBlacklist() {
        revocationService.revokeByJwtId("", "测试");
        
        assertFalse(revocationService.isRevoked("", null));
    }

    @Test
    void testRevokeByTokenValue_ShouldAddToBlacklist() {
        String tokenValue = "test-token-12345";
        String reason = "Token 泄露";
        
        revocationService.revokeByTokenValue(tokenValue, reason);
        
        assertTrue(revocationService.isRevoked(null, tokenValue));
    }

    @Test
    void testRevokeByTokenValue_WithNullTokenValue_ShouldNotThrow() {
        assertDoesNotThrow(() -> revocationService.revokeByTokenValue(null, "测试"));
    }

    @Test
    void testIsRevoked_WithBothNull_ShouldReturnFalse() {
        assertFalse(revocationService.isRevoked(null, null));
    }

    @Test
    void testIsRevoked_WithNonRevokedToken_ShouldReturnFalse() {
        String jwtId = UUID.randomUUID().toString();
        String tokenValue = "test-token-not-revoked";
        
        assertFalse(revocationService.isRevoked(jwtId, tokenValue));
    }

    @Test
    void testIsRevoked_WithRevokedJwtId_ShouldReturnTrue() {
        String jwtId = UUID.randomUUID().toString();
        
        revocationService.revokeByJwtId(jwtId, "测试吊销");
        
        assertTrue(revocationService.isRevoked(jwtId, null));
    }

    @Test
    void testIsRevoked_WithRevokedTokenValue_ShouldReturnTrue() {
        String tokenValue = "test-token-value";
        
        revocationService.revokeByTokenValue(tokenValue, "测试吊销");
        
        assertTrue(revocationService.isRevoked(null, tokenValue));
    }

    @Test
    void testRestoreToken_ShouldRemoveFromBlacklist() {
        String jwtId = UUID.randomUUID().toString();
        
        revocationService.revokeByJwtId(jwtId, "测试吊销");
        assertTrue(revocationService.isRevoked(jwtId, null));
        
        revocationService.restoreToken(jwtId);
        assertFalse(revocationService.isRevoked(jwtId, null));
    }

    @Test
    void testRestoreToken_WithNullJwtId_ShouldNotThrow() {
        assertDoesNotThrow(() -> revocationService.restoreToken(null));
    }

    @Test
    void testGetBlacklistSize_ShouldReturnCorrectCount() {
        assertEquals(0, revocationService.getBlacklistSize());
        
        revocationService.revokeByJwtId(UUID.randomUUID().toString(), "测试1");
        assertEquals(1, revocationService.getBlacklistSize());
        
        revocationService.revokeByJwtId(UUID.randomUUID().toString(), "测试2");
        assertEquals(2, revocationService.getBlacklistSize());
        
        revocationService.revokeByTokenValue("test-token", "测试3");
        assertEquals(3, revocationService.getBlacklistSize());
    }

    @Test
    void testGetBlacklistStats_ShouldReturnCorrectStats() {
        revocationService.revokeByJwtId(UUID.randomUUID().toString(), "测试1");
        revocationService.revokeByJwtId(UUID.randomUUID().toString(), "测试2");
        
        Map<String, Object> stats = revocationService.getBlacklistStats();
        
        assertNotNull(stats);
        assertEquals(2L, stats.get("size"));
    }

    @Test
    void testRevokeByJwtId_WithDefaultReason() {
        String jwtId = UUID.randomUUID().toString();
        
        revocationService.revokeByJwtId(jwtId, null);
        
        assertTrue(revocationService.isRevoked(jwtId, null));
    }

    @Test
    void testRevokeByTokenValue_WithDefaultReason() {
        String tokenValue = "test-token-reason";
        
        revocationService.revokeByTokenValue(tokenValue, null);
        
        assertTrue(revocationService.isRevoked(null, tokenValue));
    }

    @Test
    void testMultipleRevocations_ShouldWorkCorrectly() {
        String jwtId1 = UUID.randomUUID().toString();
        String jwtId2 = UUID.randomUUID().toString();
        String tokenValue1 = "token-value-1";
        String tokenValue2 = "token-value-2";
        
        revocationService.revokeByJwtId(jwtId1, "吊销1");
        revocationService.revokeByTokenValue(tokenValue1, "吊销2");
        revocationService.revokeByJwtId(jwtId2, "吊销3");
        revocationService.revokeByTokenValue(tokenValue2, "吊销4");
        
        assertEquals(4, revocationService.getBlacklistSize());
        assertTrue(revocationService.isRevoked(jwtId1, null));
        assertTrue(revocationService.isRevoked(jwtId2, null));
        assertTrue(revocationService.isRevoked(null, tokenValue1));
        assertTrue(revocationService.isRevoked(null, tokenValue2));
    }

    @Test
    void testIsRevoked_WithJwtIdNotRevokedAndTokenValueRevoked_ShouldReturnTrue() {
        String jwtId = UUID.randomUUID().toString();
        String tokenValue = "test-token-combined";
        
        revocationService.revokeByTokenValue(tokenValue, "测试吊销");
        
        assertTrue(revocationService.isRevoked(jwtId, tokenValue));
    }

    @Test
    void testIsRevoked_WithJwtIdRevokedAndTokenValueNotRevoked_ShouldReturnTrue() {
        String jwtId = UUID.randomUUID().toString();
        String tokenValue = "test-token-not-revoked";
        
        revocationService.revokeByJwtId(jwtId, "测试吊销");
        
        assertTrue(revocationService.isRevoked(jwtId, tokenValue));
    }

    @Test
    void testClearExpiredEntries_ShouldRemoveOldEntries() {
        revocationService.revokeByJwtId(UUID.randomUUID().toString(), "测试1");
        revocationService.revokeByJwtId(UUID.randomUUID().toString(), "测试2");
        
        assertEquals(2, revocationService.getBlacklistSize());
        
        revocationService.clearExpiredEntries(0);
        
        assertEquals(0, revocationService.getBlacklistSize());
    }

    @Test
    void testClearExpiredEntries_WithMaxAge_ShouldPreserveNewEntries() {
        revocationService.revokeByJwtId(UUID.randomUUID().toString(), "测试1");
        
        assertEquals(1, revocationService.getBlacklistSize());
        
        revocationService.clearExpiredEntries(3600);
        
        assertEquals(1, revocationService.getBlacklistSize());
    }
}
