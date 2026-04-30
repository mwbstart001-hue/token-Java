package com.example.tokenservice.controller;

import com.example.tokenservice.dto.ApiResponse;
import com.example.tokenservice.monitor.TokenPerformanceMonitor;
import com.example.tokenservice.revocation.TokenRevocationService;
import com.example.tokenservice.strategy.TokenStrategySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class TokenAdminController {

    private static final Logger log = LoggerFactory.getLogger(TokenAdminController.class);

    private final TokenStrategySelector strategySelector;
    private final TokenRevocationService revocationService;
    private final TokenPerformanceMonitor performanceMonitor;

    public TokenAdminController(TokenStrategySelector strategySelector,
                                 TokenRevocationService revocationService,
                                 TokenPerformanceMonitor performanceMonitor) {
        this.strategySelector = strategySelector;
        this.revocationService = revocationService;
        this.performanceMonitor = performanceMonitor;
    }

    @GetMapping("/strategy/current")
    public ApiResponse<Map<String, Object>> getCurrentStrategy() {
        Map<String, Object> result = new HashMap<>();
        result.put("strategyType", strategySelector.getCurrentStrategyType());
        result.put("algorithm", strategySelector.getCurrentAlgorithm());
        result.put("generator", strategySelector.getSelectedGenerator().getClass().getSimpleName());
        result.put("validator", strategySelector.getSelectedValidator().getClass().getSimpleName());
        
        return ApiResponse.success("获取当前策略成功", result);
    }

    @PostMapping("/strategy/switch")
    public ApiResponse<Map<String, Object>> switchStrategy(
            @RequestParam(value = "strategyType", required = true) String strategyType,
            @RequestParam(value = "algorithm", required = false, defaultValue = "RS256") String algorithm) {
        
        log.info("请求切换策略: type={}, algorithm={}", strategyType, algorithm);
        
        boolean success = strategySelector.switchStrategy(strategyType, algorithm);
        
        if (success) {
            Map<String, Object> result = new HashMap<>();
            result.put("strategyType", strategySelector.getCurrentStrategyType());
            result.put("algorithm", strategySelector.getCurrentAlgorithm());
            result.put("generator", strategySelector.getSelectedGenerator().getClass().getSimpleName());
            result.put("validator", strategySelector.getSelectedValidator().getClass().getSimpleName());
            
            return ApiResponse.success("策略切换成功", result);
        } else {
            return ApiResponse.error("策略切换失败，请检查参数");
        }
    }

    @PostMapping("/token/revoke")
    public ApiResponse<Map<String, Boolean>> revokeToken(
            @RequestParam(value = "jwtId", required = false) String jwtId,
            @RequestParam(value = "tokenValue", required = false) String tokenValue,
            @RequestParam(value = "reason", required = false, defaultValue = "手动吊销") String reason) {
        
        if (jwtId == null && tokenValue == null) {
            return ApiResponse.error("必须提供 jwtId 或 tokenValue");
        }

        if (jwtId != null && !jwtId.isEmpty()) {
            revocationService.revokeByJwtId(jwtId, reason);
        }
        if (tokenValue != null && !tokenValue.isEmpty()) {
            revocationService.revokeByTokenValue(tokenValue, reason);
        }

        Map<String, Boolean> result = new HashMap<>();
        result.put("revoked", true);
        
        return ApiResponse.success("Token 已吊销", result);
    }

    @PostMapping("/token/restore")
    public ApiResponse<Map<String, Boolean>> restoreToken(
            @RequestParam(value = "jwtId", required = true) String jwtId) {
        
        revocationService.restoreToken(jwtId);
        
        Map<String, Boolean> result = new HashMap<>();
        result.put("restored", true);
        
        return ApiResponse.success("Token 已从黑名单恢复", result);
    }

    @GetMapping("/blacklist/stats")
    public ApiResponse<Map<String, Object>> getBlacklistStats() {
        Map<String, Object> stats = revocationService.getBlacklistStats();
        return ApiResponse.success("获取黑名单统计成功", stats);
    }

    @GetMapping("/performance/stats")
    public ApiResponse<Map<String, Object>> getPerformanceStats() {
        Map<String, Object> result = new HashMap<>();
        
        Map<String, Object> generateStats = new HashMap<>();
        for (Map.Entry<String, TokenPerformanceMonitor.StrategyPerformanceStats> entry : 
                performanceMonitor.getAllGenerateStats().entrySet()) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("totalCount", entry.getValue().getTotalCount());
            stat.put("errorCount", entry.getValue().getErrorCount());
            stat.put("errorRate", entry.getValue().getErrorRate());
            stat.put("avgTimeMs", entry.getValue().getAvgTimeMs());
            stat.put("minTimeMs", entry.getValue().getMinTimeMs());
            stat.put("maxTimeMs", entry.getValue().getMaxTimeMs());
            stat.put("totalTimeMs", entry.getValue().getTotalTimeMs());
            generateStats.put(entry.getKey(), stat);
        }
        
        Map<String, Object> validateStats = new HashMap<>();
        for (Map.Entry<String, TokenPerformanceMonitor.StrategyPerformanceStats> entry : 
                performanceMonitor.getAllValidateStats().entrySet()) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("totalCount", entry.getValue().getTotalCount());
            stat.put("errorCount", entry.getValue().getErrorCount());
            stat.put("errorRate", entry.getValue().getErrorRate());
            stat.put("avgTimeMs", entry.getValue().getAvgTimeMs());
            stat.put("minTimeMs", entry.getValue().getMinTimeMs());
            stat.put("maxTimeMs", entry.getValue().getMaxTimeMs());
            stat.put("totalTimeMs", entry.getValue().getTotalTimeMs());
            validateStats.put(entry.getKey(), stat);
        }
        
        result.put("generate", generateStats);
        result.put("validate", validateStats);
        
        return ApiResponse.success("获取性能统计成功", result);
    }

    @PostMapping("/performance/reset")
    public ApiResponse<Void> resetPerformanceStats() {
        performanceMonitor.resetAllGenerateStats();
        performanceMonitor.resetAllValidateStats();
        
        return ApiResponse.success("性能统计已重置", null);
    }
}
