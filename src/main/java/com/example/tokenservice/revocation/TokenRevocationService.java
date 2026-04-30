package com.example.tokenservice.revocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final TokenRevocationStore revocationStore;

    public TokenRevocationService(TokenRevocationStore revocationStore) {
        this.revocationStore = revocationStore;
        log.info("Token 吊销黑名单服务已初始化");
    }

    public void revokeByJwtId(String jwtId, String reason) {
        if (jwtId == null || jwtId.isEmpty()) {
            log.warn("尝试吊销空 jwtId");
            return;
        }
        revocationStore.revokeByJwtId(jwtId, reason != null ? reason : "手动吊销");
        log.info("Token 已吊销（jwtId）: {}", jwtId);
    }

    public void revokeByTokenValue(String tokenValue, String reason) {
        if (tokenValue == null || tokenValue.isEmpty()) {
            log.warn("尝试吊销空 tokenValue");
            return;
        }
        revocationStore.revokeByTokenValue(tokenValue, reason != null ? reason : "手动吊销");
        log.info("Token 已吊销（tokenValue）");
    }

    public boolean isRevoked(String jwtId, String tokenValue) {
        return revocationStore.isRevoked(jwtId, tokenValue);
    }

    public void restoreToken(String jwtId) {
        if (jwtId == null || jwtId.isEmpty()) {
            log.warn("尝试恢复空 jwtId");
            return;
        }
        revocationStore.removeFromBlacklist(jwtId);
        log.info("Token 已从黑名单恢复: {}", jwtId);
    }

    public long getBlacklistSize() {
        return revocationStore.getBlacklistSize();
    }

    public void clearExpiredEntries(long maxAgeSeconds) {
        revocationStore.clearExpiredEntries(maxAgeSeconds);
    }

    public Map<String, Object> getBlacklistStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("size", getBlacklistSize());
        return stats;
    }

    public BatchRevokeResult batchRevokeByJwtIds(List<String> jwtIds, String reason) {
        BatchRevokeResult result = new BatchRevokeResult();
        
        if (jwtIds == null || jwtIds.isEmpty()) {
            return result;
        }
        
        String actualReason = reason != null ? reason : "批量吊销";
        
        for (int i = 0; i < jwtIds.size(); i++) {
            String jwtId = jwtIds.get(i);
            if (jwtId != null && !jwtId.isEmpty()) {
                try {
                    revocationStore.revokeByJwtId(jwtId, actualReason);
                    result.addSuccess(jwtId);
                    log.debug("批量吊销成功 - jwtId: {}", jwtId);
                } catch (Exception e) {
                    log.error("批量吊销失败 - jwtId: {}, 错误: {}", jwtId, e.getMessage());
                    result.addFailure(jwtId, e.getMessage());
                }
            }
        }
        
        log.info("批量吊销完成 - 成功: {}, 失败: {}", 
                result.getSuccessCount(), result.getFailureCount());
        return result;
    }

    public BatchRevokeResult batchRevokeByTokenValues(List<String> tokenValues, String reason) {
        BatchRevokeResult result = new BatchRevokeResult();
        
        if (tokenValues == null || tokenValues.isEmpty()) {
            return result;
        }
        
        String actualReason = reason != null ? reason : "批量吊销";
        
        for (int i = 0; i < tokenValues.size(); i++) {
            String tokenValue = tokenValues.get(i);
            if (tokenValue != null && !tokenValue.isEmpty()) {
                try {
                    revocationStore.revokeByTokenValue(tokenValue, actualReason);
                    result.addSuccess(tokenValue);
                    log.debug("批量吊销成功 - tokenValue: {}", tokenValue);
                } catch (Exception e) {
                    log.error("批量吊销失败 - tokenValue: {}, 错误: {}", tokenValue, e.getMessage());
                    result.addFailure(tokenValue, e.getMessage());
                }
            }
        }
        
        log.info("批量吊销完成 - 成功: {}, 失败: {}", 
                result.getSuccessCount(), result.getFailureCount());
        return result;
    }

    public static class BatchRevokeResult {
        private int totalCount;
        private int successCount;
        private int failureCount;
        private List<String> successfulItems = new ArrayList<>();
        private List<FailureItem> failures = new ArrayList<>();

        public void addSuccess(String id) {
            successfulItems.add(id);
            successCount++;
            totalCount++;
        }

        public void addFailure(String id, String message) {
            failures.add(new FailureItem(id, message));
            failureCount++;
            totalCount++;
        }

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

        public List<String> getSuccessfulItems() {
            return successfulItems;
        }

        public void setSuccessfulItems(List<String> successfulItems) {
            this.successfulItems = successfulItems;
        }

        public List<FailureItem> getFailures() {
            return failures;
        }

        public void setFailures(List<FailureItem> failures) {
            this.failures = failures;
        }
    }

    public static class FailureItem {
        private String id;
        private String message;

        public FailureItem() {
        }

        public FailureItem(String id, String message) {
            this.id = id;
            this.message = message;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
