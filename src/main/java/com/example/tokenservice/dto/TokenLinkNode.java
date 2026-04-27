package com.example.tokenservice.dto;

import com.example.tokenservice.model.TokenOperationType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Token链路追踪节点DTO
 * 用于构建Token完整生命周期的树形结构
 */
public class TokenLinkNode {

    private String jwtId;
    private String parentJwtId;
    private TokenOperationType operationType;
    private LocalDateTime operationTime;
    private String userId;
    private boolean success;
    private String failureReason;
    private List<TokenLinkNode> children;

    public TokenLinkNode() {
        this.children = new ArrayList<>();
    }

    public TokenLinkNode(String jwtId, TokenOperationType operationType, LocalDateTime operationTime) {
        this.jwtId = jwtId;
        this.operationType = operationType;
        this.operationTime = operationTime;
        this.children = new ArrayList<>();
    }

    public String getJwtId() {
        return jwtId;
    }

    public void setJwtId(String jwtId) {
        this.jwtId = jwtId;
    }

    public String getParentJwtId() {
        return parentJwtId;
    }

    public void setParentJwtId(String parentJwtId) {
        this.parentJwtId = parentJwtId;
    }

    public TokenOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(TokenOperationType operationType) {
        this.operationType = operationType;
    }

    public LocalDateTime getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(LocalDateTime operationTime) {
        this.operationTime = operationTime;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public List<TokenLinkNode> getChildren() {
        return children;
    }

    public void setChildren(List<TokenLinkNode> children) {
        this.children = children;
    }

    public void addChild(TokenLinkNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
