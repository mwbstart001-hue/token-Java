package com.example.tokenservice.common;

public class TraceContext {
    
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    
    public static String getTraceId() {
        return TRACE_ID.get();
    }
    
    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }
    
    public static void clear() {
        TRACE_ID.remove();
    }
}
