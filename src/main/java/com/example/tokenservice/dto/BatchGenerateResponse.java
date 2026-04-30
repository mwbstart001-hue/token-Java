package com.example.tokenservice.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchGenerateResponse {

    private int totalCount;
    private int successCount;
    private int failureCount;
    private List<String> tokens = new ArrayList<>();
    private List<FailureItem> failures = new ArrayList<>();

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<String> getTokens() {
        return tokens;
    }

    public void setTokens(List<String> tokens) {
        this.tokens = tokens;
    }

    public List<FailureItem> getFailures() {
        return failures;
    }

    public void setFailures(List<FailureItem> failures) {
        this.failures = failures;
    }

    public void addSuccessToken(String token) {
        this.tokens.add(token);
        this.successCount++;
        this.totalCount++;
    }

    public void addFailure(int index, String message) {
        this.failures.add(new FailureItem(index, message));
        this.failureCount++;
        this.totalCount++;
    }

    public static class FailureItem {
        private int index;
        private String message;

        public FailureItem() {
        }

        public FailureItem(int index, String message) {
            this.index = index;
            this.message = message;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
