package com.example.tokenservice.common;

public class TenantContext {
    
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    
    private static final String DEFAULT_TENANT_ID = "default";
    
    public static String getTenantId() {
        String tenantId = TENANT_ID.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }
    
    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }
    
    public static void clear() {
        TENANT_ID.remove();
    }
}
