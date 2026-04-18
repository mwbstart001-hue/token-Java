package com.example.tokenservice.controller;

import com.example.tokenservice.common.ResultCode;
import com.example.tokenservice.common.TraceContext;
import com.example.tokenservice.dto.*;
import com.example.tokenservice.ratelimit.RateLimitService;
import com.example.tokenservice.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/token")
public class TokenController {
    
    private final TokenService tokenService;
    private final RateLimitService rateLimitService;
    
    private static final int LOGIN_LIMIT = 5;
    private static final int REFRESH_LIMIT = 10;
    private static final int VALIDATE_LIMIT = 20;
    private static final long WINDOW_MS = 60000;
    
    @Autowired
    public TokenController(TokenService tokenService, RateLimitService rateLimitService) {
        this.tokenService = tokenService;
        this.rateLimitService = rateLimitService;
    }
    
    @PostMapping("/login")
    public ApiResponse<TokenPair> login(@Valid @RequestBody TokenRequest request, HttpServletRequest httpRequest) {
        String traceId = TraceContext.getTraceId();
        String clientIp = getClientIp(httpRequest);
        
        String rateLimitKey = "login:" + clientIp;
        if (!rateLimitService.tryAcquire(rateLimitKey, LOGIN_LIMIT, WINDOW_MS)) {
            log.warn("[{}] Login rate limit exceeded for IP: {}", traceId, clientIp);
            return ApiResponse.error(429, "请求过于频繁，请稍后重试");
        }
        
        log.info("[{}] Login request - userId: {}, IP: {}", traceId, request.getUserId(), clientIp);
        
        TokenPair tokenPair = tokenService.generateTokenPair(
                request.getUserId(),
                request.getUsername()
        );
        
        log.info("[{}] Login successful for userId: {}", traceId, request.getUserId());
        
        return ApiResponse.success("登录成功", tokenPair);
    }
    
    @PostMapping("/refresh")
    public ApiResponse<TokenPair> refreshToken(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String traceId = TraceContext.getTraceId();
        String clientIp = getClientIp(httpRequest);
        
        String rateLimitKey = "refresh:" + clientIp;
        if (!rateLimitService.tryAcquire(rateLimitKey, REFRESH_LIMIT, WINDOW_MS)) {
            log.warn("[{}] Refresh token rate limit exceeded for IP: {}", traceId, clientIp);
            return ApiResponse.error(429, "请求过于频繁，请稍后重试");
        }
        
        log.info("[{}] Refresh token request, IP: {}", traceId, clientIp);
        
        try {
            TokenPair tokenPair = tokenService.refreshToken(request.getRefreshToken());
            log.info("[{}] Token refreshed successfully", traceId);
            return ApiResponse.success("Token 刷新成功", tokenPair);
        } catch (IllegalArgumentException e) {
            log.warn("[{}] Refresh token failed: {}", traceId, e.getMessage());
            return ApiResponse.error(ResultCode.TOKEN_INVALID.getCode(), e.getMessage());
        }
    }
    
    @PostMapping("/validate")
    public ApiResponse<ValidationResult> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest httpRequest) {
        
        String traceId = TraceContext.getTraceId();
        String clientIp = getClientIp(httpRequest);
        
        String rateLimitKey = "validate:" + clientIp;
        if (!rateLimitService.tryAcquire(rateLimitKey, VALIDATE_LIMIT, WINDOW_MS)) {
            log.warn("[{}] Validate token rate limit exceeded for IP: {}", traceId, clientIp);
            return ApiResponse.error(429, "请求过于频繁，请稍后重试");
        }
        
        String token = extractToken(authorization);
        
        if (!StringUtils.hasText(token)) {
            log.warn("[{}] Validate token request - missing token, IP: {}", traceId, clientIp);
            return ApiResponse.error(
                    ResultCode.TOKEN_INVALID.getCode(),
                    "缺少 Token"
            );
        }
        
        log.info("[{}] Validate token request, IP: {}", traceId, clientIp);
        
        ValidationResult result = tokenService.validateToken(token);
        
        return ApiResponse.success(result);
    }
    
    @PostMapping("/revoke")
    public ApiResponse<Boolean> revokeToken(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest httpRequest) {
        
        String traceId = TraceContext.getTraceId();
        String clientIp = getClientIp(httpRequest);
        
        String token = extractToken(authorization);
        
        if (!StringUtils.hasText(token)) {
            log.warn("[{}] Revoke token request - missing token, IP: {}", traceId, clientIp);
            return ApiResponse.error(
                    ResultCode.BAD_REQUEST.getCode(),
                    "缺少 Token"
            );
        }
        
        log.info("[{}] Revoke token request, IP: {}", traceId, clientIp);
        
        boolean revoked = tokenService.revokeToken(token);
        
        if (revoked) {
            return ApiResponse.success("Token 作废成功", true);
        } else {
            return ApiResponse.error(
                    ResultCode.BAD_REQUEST.getCode(),
                    "Token 作废失败或已过期"
            );
        }
    }
    
    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest httpRequest) {
        
        String traceId = TraceContext.getTraceId();
        String clientIp = getClientIp(httpRequest);
        
        String token = extractToken(authorization);
        
        if (!StringUtils.hasText(token)) {
            log.warn("[{}] Logout request - missing token, IP: {}", traceId, clientIp);
            return ApiResponse.error(
                    ResultCode.BAD_REQUEST.getCode(),
                    "缺少 Token"
            );
        }
        
        log.info("[{}] Logout request, IP: {}", traceId, clientIp);
        
        try {
            tokenService.revokeToken(token);
            log.info("[{}] Logout successful", traceId);
            return ApiResponse.success("登出成功", true);
        } catch (Exception e) {
            log.error("[{}] Logout failed: {}", traceId, e.getMessage(), e);
            return ApiResponse.error(
                    ResultCode.INTERNAL_ERROR.getCode(),
                    "登出失败"
            );
        }
    }
    
    @PostMapping("/revoke/user/{userId}")
    public ApiResponse<Boolean> revokeTokenByUserId(@PathVariable String userId, HttpServletRequest httpRequest) {
        String traceId = TraceContext.getTraceId();
        String clientIp = getClientIp(httpRequest);
        
        if (!StringUtils.hasText(userId)) {
            log.warn("[{}] Revoke token by userId request - missing userId, IP: {}", traceId, clientIp);
            return ApiResponse.error(
                    ResultCode.BAD_REQUEST.getCode(),
                    "缺少用户 ID"
            );
        }
        
        log.info("[{}] Revoke token by userId: {}, IP: {}", traceId, userId, clientIp);
        
        boolean revoked = tokenService.revokeTokenByUserId(userId);
        
        if (revoked) {
            return ApiResponse.success("用户 Token 作废成功", true);
        } else {
            return ApiResponse.error(
                    ResultCode.NOT_FOUND.getCode(),
                    "未找到该用户的有效 Token"
            );
        }
    }
    
    private String extractToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        
        return authorization;
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
        return ip;
    }
}
