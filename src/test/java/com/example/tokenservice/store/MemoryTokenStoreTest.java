package com.example.tokenservice.store;

import com.example.tokenservice.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MemoryTokenStoreTest {

    private MemoryTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        tokenStore = new MemoryTokenStore();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void saveAndGetAccessToken_ShouldWorkWithinTenant() {
        TenantContext.setTenantId("tenant-1");
        String userId = "user-1";
        String token = "access-token-1";
        long ttl = 60000;

        tokenStore.saveAccessToken(userId, token, ttl);
        Optional<String> retrieved = tokenStore.getAccessToken(userId);

        assertTrue(retrieved.isPresent());
        assertEquals(token, retrieved.get());
    }

    @Test
    void saveAndGetRefreshToken_ShouldWorkWithinTenant() {
        TenantContext.setTenantId("tenant-1");
        String userId = "user-1";
        String token = "refresh-token-1";
        long ttl = 60000;

        tokenStore.saveRefreshToken(userId, token, ttl);
        Optional<String> retrieved = tokenStore.getRefreshToken(userId);

        assertTrue(retrieved.isPresent());
        assertEquals(token, retrieved.get());
    }

    @Test
    void tokens_ShouldBeIsolatedBetweenTenants() {
        String userId = "user-1";
        String token1 = "token-tenant-1";
        String token2 = "token-tenant-2";
        long ttl = 60000;

        TenantContext.setTenantId("tenant-1");
        tokenStore.saveAccessToken(userId, token1, ttl);

        TenantContext.setTenantId("tenant-2");
        tokenStore.saveAccessToken(userId, token2, ttl);

        TenantContext.setTenantId("tenant-1");
        Optional<String> tokenForTenant1 = tokenStore.getAccessToken(userId);
        assertTrue(tokenForTenant1.isPresent());
        assertEquals(token1, tokenForTenant1.get());

        TenantContext.setTenantId("tenant-2");
        Optional<String> tokenForTenant2 = tokenStore.getAccessToken(userId);
        assertTrue(tokenForTenant2.isPresent());
        assertEquals(token2, tokenForTenant2.get());
    }

    @Test
    void removeAccessToken_ShouldRemoveWithinTenant() {
        TenantContext.setTenantId("tenant-1");
        String userId = "user-1";
        String token = "access-token-1";
        long ttl = 60000;

        tokenStore.saveAccessToken(userId, token, ttl);
        tokenStore.removeAccessToken(userId);
        Optional<String> retrieved = tokenStore.getAccessToken(userId);

        assertFalse(retrieved.isPresent());
    }

    @Test
    void addToBlacklist_ShouldWorkWithinTenant() {
        TenantContext.setTenantId("tenant-1");
        String token = "blacklisted-token";
        long ttl = 60000;

        tokenStore.addToBlacklist(token, ttl);
        boolean isBlacklisted = tokenStore.isBlacklisted(token);

        assertTrue(isBlacklisted);
    }

    @Test
    void blacklist_ShouldBeIsolatedBetweenTenants() {
        String token = "test-token";
        long ttl = 60000;

        TenantContext.setTenantId("tenant-1");
        tokenStore.addToBlacklist(token, ttl);

        TenantContext.setTenantId("tenant-2");
        boolean isBlacklistedInTenant2 = tokenStore.isBlacklisted(token);

        assertFalse(isBlacklistedInTenant2);
    }

    @Test
    void validateAccessToken_ShouldReturnTrue_WhenValid() {
        TenantContext.setTenantId("tenant-1");
        String userId = "user-1";
        String token = "valid-token";
        long ttl = 60000;

        tokenStore.saveAccessToken(userId, token, ttl);
        boolean isValid = tokenStore.validateAccessToken(userId, token);

        assertTrue(isValid);
    }

    @Test
    void validateAccessToken_ShouldReturnFalse_WhenTokenMismatch() {
        TenantContext.setTenantId("tenant-1");
        String userId = "user-1";
        String token1 = "token-1";
        String token2 = "token-2";
        long ttl = 60000;

        tokenStore.saveAccessToken(userId, token1, ttl);
        boolean isValid = tokenStore.validateAccessToken(userId, token2);

        assertFalse(isValid);
    }

    @Test
    void markRefreshTokenUsed_ShouldWorkWithinTenant() {
        TenantContext.setTenantId("tenant-1");
        String oldToken = "old-refresh-token";
        String newToken = "new-refresh-token";
        String userId = "user-1";
        long ttl = 60000;

        tokenStore.markRefreshTokenUsed(oldToken, newToken, userId, ttl);
        boolean isUsed = tokenStore.isRefreshTokenUsed(oldToken);

        assertTrue(isUsed);
    }

    @Test
    void usedRefreshTokens_ShouldBeIsolatedBetweenTenants() {
        String oldToken = "old-token";
        String newToken = "new-token";
        String userId = "user-1";
        long ttl = 60000;

        TenantContext.setTenantId("tenant-1");
        tokenStore.markRefreshTokenUsed(oldToken, newToken, userId, ttl);

        TenantContext.setTenantId("tenant-2");
        boolean isUsedInTenant2 = tokenStore.isRefreshTokenUsed(oldToken);

        assertFalse(isUsedInTenant2);
    }
}
