package com.example.tokenservice.dto;

import java.util.List;

public class BatchRevokeRequest {

    private List<String> jwtIds;
    private List<String> tokenValues;
    private String reason = "批量吊销";

    public List<String> getJwtIds() {
        return jwtIds;
    }

    public void setJwtIds(List<String> jwtIds) {
        this.jwtIds = jwtIds;
    }

    public List<String> getTokenValues() {
        return tokenValues;
    }

    public void setTokenValues(List<String> tokenValues) {
        this.tokenValues = tokenValues;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
