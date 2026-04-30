package com.example.tokenservice.controller;

import com.example.tokenservice.dto.ApiResponse;
import com.example.tokenservice.dto.BatchGenerateRequest;
import com.example.tokenservice.dto.BatchGenerateResponse;
import com.example.tokenservice.dto.BatchRevokeRequest;
import com.example.tokenservice.dto.TokenGenerateRequest;
import com.example.tokenservice.dto.TokenInfo;
import com.example.tokenservice.dto.TokenRenewRequest;
import com.example.tokenservice.ratelimit.RateLimitService;
import com.example.tokenservice.revocation.TokenRevocationService;
import com.example.tokenservice.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/token")
public class TokenController {

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    private final TokenService tokenService;
    private final RateLimitService rateLimitService;
    private final TokenRevocationService tokenRevocationService;

    public TokenController(TokenService tokenService,
                           RateLimitService rateLimitService,
                           TokenRevocationService tokenRevocationService) {
        this.tokenService = tokenService;
        this.rateLimitService = rateLimitService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, String>> generateToken(@Validated @RequestBody TokenGenerateRequest request,
                                                            HttpServletRequest httpRequest) {
        String userId = request.getUserId();
        String clientIp = getClientIp(httpRequest);
        
        boolean allowed = rateLimitService.tryAcquire(userId, clientIp);
        if (!allowed) {
            return ApiResponse.tooManyRequests("请求频率超过限制，请稍后重试");
        }
        
        String token = tokenService.generateToken(
                userId,
                request.getSubject(),
                request.getExpireSeconds()
        );
        
        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        
        return ApiResponse.success("Token 生成成功", result);
    }

    @GetMapping("/validate")
    public ApiResponse<Map<String, Boolean>> validateToken(@RequestParam("token") String tokenValue) {
        boolean valid = tokenService.validateToken(tokenValue);
        
        Map<String, Boolean> result = new HashMap<>();
        result.put("valid", valid);
        
        return ApiResponse.success(valid ? "Token 有效" : "Token 无效或已过期", result);
    }

    @GetMapping("/info")
    public ApiResponse<TokenInfo> getTokenInfo(@RequestParam("token") String tokenValue) {
        Optional<TokenInfo> tokenInfo = tokenService.getTokenInfo(tokenValue);
        
        if (tokenInfo.isPresent()) {
            return ApiResponse.success("获取 Token 信息成功", tokenInfo.get());
        } else {
            return ApiResponse.error("Token 不存在");
        }
    }

    @PostMapping("/invalidate")
    public ApiResponse<Map<String, Boolean>> invalidateToken(@RequestParam("token") String tokenValue) {
        boolean success = tokenService.invalidateToken(tokenValue);
        
        Map<String, Boolean> result = new HashMap<>();
        result.put("success", success);
        
        if (success) {
            return ApiResponse.success("Token 作废成功", result);
        } else {
            return ApiResponse.error("Token 不存在或已作废");
        }
    }

    @PostMapping("/clear-expired")
    public ApiResponse<Void> clearExpiredTokens() {
        tokenService.clearExpiredTokens();
        return ApiResponse.success("已清理过期 Token", null);
    }

    @PostMapping("/renew")
    public ApiResponse<Map<String, String>> renewToken(@Validated @RequestBody TokenRenewRequest request) {
        String newToken = tokenService.renewToken(
                request.getToken(),
                request.getExpireSeconds(),
                request.isInvalidateOldToken()
        );
        
        if (newToken != null) {
            Map<String, String> result = new HashMap<>();
            result.put("token", newToken);
            return ApiResponse.success("Token 续签成功", result);
        } else {
            return ApiResponse.error("Token 无效或已过期");
        }
    }

    @PostMapping("/batch/generate")
    public ApiResponse<BatchGenerateResponse> batchGenerateTokens(
            @Validated @RequestBody BatchGenerateRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = request.getUserId();
        String clientIp = getClientIp(httpRequest);
        int count = request.getCount();
        
        boolean allowed = rateLimitService.tryAcquireBatch(userId, clientIp, count);
        if (!allowed) {
            return ApiResponse.tooManyRequests("批量生成请求频率超过限制，请稍后重试");
        }
        
        BatchGenerateResponse response = tokenService.batchGenerateTokens(
                userId,
                request.getSubject(),
                request.getExpireSeconds(),
                count
        );
        
        return ApiResponse.success("批量生成完成", response);
    }

    @PostMapping("/batch/revoke")
    public ApiResponse<Map<String, Object>> batchRevokeTokens(
            @RequestBody BatchRevokeRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        TokenRevocationService.BatchRevokeResult jwtIdResult = null;
        TokenRevocationService.BatchRevokeResult tokenValueResult = null;
        
        if (request.getJwtIds() != null && !request.getJwtIds().isEmpty()) {
            jwtIdResult = tokenRevocationService.batchRevokeByJwtIds(
                    request.getJwtIds(), 
                    request.getReason()
            );
        }
        
        if (request.getTokenValues() != null && !request.getTokenValues().isEmpty()) {
            tokenValueResult = tokenRevocationService.batchRevokeByTokenValues(
                    request.getTokenValues(), 
                    request.getReason()
            );
        }
        
        int totalSuccess = 0;
        int totalFailure = 0;
        
        if (jwtIdResult != null) {
            totalSuccess += jwtIdResult.getSuccessCount();
            totalFailure += jwtIdResult.getFailureCount();
            result.put("jwtIdResult", jwtIdResult);
        }
        
        if (tokenValueResult != null) {
            totalSuccess += tokenValueResult.getSuccessCount();
            totalFailure += tokenValueResult.getFailureCount();
            result.put("tokenValueResult", tokenValueResult);
        }
        
        result.put("totalSuccess", totalSuccess);
        result.put("totalFailure", totalFailure);
        
        return ApiResponse.success("批量吊销完成", result);
    }

    @GetMapping("/rate-limit/info")
    public ApiResponse<Map<String, Object>> getRateLimitInfo(
            @RequestParam(value = "userId", required = false) String userId,
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(userId, clientIp);
        
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", info.isEnabled());
        result.put("userLimit", info.getUserLimit());
        result.put("userRemaining", info.getUserRemaining());
        result.put("ipLimit", info.getIpLimit());
        result.put("ipRemaining", info.getIpRemaining());
        result.put("maxBatchSize", info.getMaxBatchSize());
        result.put("clientIp", clientIp);
        
        return ApiResponse.success("获取限流信息成功", result);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}
