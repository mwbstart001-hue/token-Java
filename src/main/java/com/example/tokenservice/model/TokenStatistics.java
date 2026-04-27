package com.example.tokenservice.model;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Token操作统计记录
 * 记录每个Token操作的详细信息，用于统计分析
 */
@Entity
@Table(name = "token_statistics", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_operation_type", columnList = "operation_type"),
        @Index(name = "idx_operation_time", columnList = "operation_time")
})
public class TokenStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "jwt_id", length = 255)
    private String jwtId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 50)
    private TokenOperationType operationType;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "operation_time", nullable = false)
    private LocalDateTime operationTime;

    @Column(name = "source_ip", length = 50)
    private String sourceIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    public TokenStatistics() {
        this.operationTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getJwtId() {
        return jwtId;
    }

    public void setJwtId(String jwtId) {
        this.jwtId = jwtId;
    }

    public TokenOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(TokenOperationType operationType) {
        this.operationType = operationType;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(LocalDateTime operationTime) {
        this.operationTime = operationTime;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
