package com.example.tokenservice.dto;

import com.example.tokenservice.model.TokenOperationType;

import java.time.LocalDateTime;

/**
 * Token统计摘要DTO
 * 用于返回按用户和操作类型聚合的统计数据
 */
public class TokenStatisticsSummary {

    private String userId;
    private TokenOperationType operationType;
    private long totalCount;
    private long successCount;
    private long failureCount;
    private double successRate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public TokenStatisticsSummary() {
    }

    public TokenStatisticsSummary(String userId, TokenOperationType operationType,
                                   long totalCount, long successCount, long failureCount) {
        this.userId = userId;
        this.operationType = operationType;
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0.0;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public TokenOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(TokenOperationType operationType) {
        this.operationType = operationType;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(long successCount) {
        this.successCount = successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(long failureCount) {
        this.failureCount = failureCount;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
