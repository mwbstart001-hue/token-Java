package com.example.tokenservice.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getTenantId_ShouldReturnDefault_WhenNotSet() {
        String tenantId = TenantContext.getTenantId();
        assertEquals("default", tenantId);
    }

    @Test
    void setTenantId_ShouldSetTenantId() {
        String expectedTenantId = "tenant-123";
        TenantContext.setTenantId(expectedTenantId);
        
        String actualTenantId = TenantContext.getTenantId();
        assertEquals(expectedTenantId, actualTenantId);
    }

    @Test
    void clear_ShouldClearTenantId() {
        TenantContext.setTenantId("tenant-456");
        TenantContext.clear();
        
        String tenantId = TenantContext.getTenantId();
        assertEquals("default", tenantId);
    }

    @Test
    void getTenantId_ShouldBeThreadLocal() throws InterruptedException {
        String mainThreadTenant = "main-tenant";
        TenantContext.setTenantId(mainThreadTenant);
        
        final String[] otherThreadTenant = new String[1];
        Thread otherThread = new Thread(() -> {
            otherThreadTenant[0] = TenantContext.getTenantId();
        });
        otherThread.start();
        otherThread.join();
        
        assertEquals("default", otherThreadTenant[0]);
        assertEquals(mainThreadTenant, TenantContext.getTenantId());
    }
}
