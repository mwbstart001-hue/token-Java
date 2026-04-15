package com.example.tokenservice.controller;

import com.example.tokenservice.common.ResultCode;
import com.example.tokenservice.common.TraceContext;
import com.example.tokenservice.dto.ApiResponse;
import com.example.tokenservice.dto.TokenRequest;
import com.example.tokenservice.dto.TokenResponse;
import com.example.tokenservice.dto.ValidationResult;
import com.example.tokenservice.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/token")
public class TokenController {
    
    private final TokenService tokenService;
    
    @Autowired
    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }
    
    @PostMapping("/generate")
    public ApiResponse<TokenResponse> generateToken(@Valid @RequestBody TokenRequest request) {
        String traceId = TraceContext.getTraceId();
        
        log.info("[{}] Generate token request - userId: {}", traceId, request.getUserId());
        
        TokenResponse tokenResponse = tokenService.generateToken(
                request.getUserId(),
                request.getUsername()
        );
        
        log.info("[{}] Token generated successfully for userId: {}", traceId, request.getUserId());
        
        return ApiResponse.success("Token 生成成功", tokenResponse);
    }
    
    @PostMapping("/validate")
    public ApiResponse<ValidationResult> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        String traceId = TraceContext.getTraceId();
        
        String token = extractToken(authorization);
        
        if (!StringUtils.hasText(token)) {
            log.warn("[{}] Validate token request - missing token", traceId);
            return ApiResponse.error(
                    ResultCode.TOKEN_INVALID.getCode(),
                    "缺少 Token"
            );
        }
        
        log.info("[{}] Validate token request", traceId);
        
        ValidationResult result = tokenService.validateToken(token);
        
        return ApiResponse.success(result);
    }
    
    @PostMapping("/revoke")
    public ApiResponse<Boolean> revokeToken(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        String traceId = TraceContext.getTraceId();
        
        String token = extractToken(authorization);
        
        if (!StringUtils.hasText(token)) {
            log.warn("[{}] Revoke token request - missing token", traceId);
            return ApiResponse.error(
                    ResultCode.BAD_REQUEST.getCode(),
                    "缺少 Token"
            );
        }
        
        log.info("[{}] Revoke token request", traceId);
        
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
    
    @PostMapping("/revoke/user/{userId}")
    public ApiResponse<Boolean> revokeTokenByUserId(@PathVariable String userId) {
        String traceId = TraceContext.getTraceId();
        
        if (!StringUtils.hasText(userId)) {
            log.warn("[{}] Revoke token by userId request - missing userId", traceId);
            return ApiResponse.error(
                    ResultCode.BAD_REQUEST.getCode(),
                    "缺少用户 ID"
            );
        }
        
        log.info("[{}] Revoke token by userId: {}", traceId, userId);
        
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
}
