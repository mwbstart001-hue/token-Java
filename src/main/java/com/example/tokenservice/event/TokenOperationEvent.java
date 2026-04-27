package com.example.tokenservice.event;

import com.example.tokenservice.model.TokenOperationType;
import org.springframework.context.ApplicationEvent;

/**
 * Token操作事件
 * 用于异步记录Token操作统计
 */
public class TokenOperationEvent extends ApplicationEvent {

    private final String userId;
    private final String jwtId;
    private final String parentJwtId;
    private final String tokenValue;
    private final TokenOperationType operationType;
    private final boolean success;
    private final String failureReason;
    private final String sourceIp;
    private final String userAgent;

    public TokenOperationEvent(Object source, String userId, String jwtId, String parentJwtId, String tokenValue,
                                TokenOperationType operationType, boolean success, String failureReason,
                                String sourceIp, String userAgent) {
        super(source);
        this.userId = userId;
        this.jwtId = jwtId;
        this.parentJwtId = parentJwtId;
        this.tokenValue = tokenValue;
        this.operationType = operationType;
        this.success = success;
        this.failureReason = failureReason;
        this.sourceIp = sourceIp;
        this.userAgent = userAgent;
    }

    public String getUserId() {
        return userId;
    }

    public String getJwtId() {
        return jwtId;
    }

    public String getParentJwtId() {
        return parentJwtId;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public TokenOperationType getOperationType() {
        return operationType;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
