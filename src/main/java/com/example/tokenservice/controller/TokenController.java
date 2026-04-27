package com.example.tokenservice.controller;

import com.example.tokenservice.dto.ApiResponse;
import com.example.tokenservice.dto.TokenGenerateRequest;
import com.example.tokenservice.dto.TokenInfo;
import com.example.tokenservice.dto.TokenRenewRequest;
import com.example.tokenservice.service.TokenService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/token")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, String>> generateToken(@Validated @RequestBody TokenGenerateRequest request) {
        String token = tokenService.generateToken(
                request.getUserId(),
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
}
