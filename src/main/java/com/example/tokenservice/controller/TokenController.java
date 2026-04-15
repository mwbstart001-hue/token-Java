package com.example.tokenservice.controller;

import com.example.tokenservice.dto.ApiResponse;
import com.example.tokenservice.dto.TokenRequest;
import com.example.tokenservice.dto.TokenResponse;
import com.example.tokenservice.dto.ValidationResult;
import com.example.tokenservice.service.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/token")
@RequiredArgsConstructor
public class TokenController {
    
    private final TokenService tokenService;
    
    @PostMapping("/generate")
    public ApiResponse<TokenResponse> generateToken(@Valid @RequestBody TokenRequest request) {
        TokenResponse tokenResponse = tokenService.generateToken(
                request.getUserId(),
                request.getUsername()
        );
        return ApiResponse.success("Token 生成成功", tokenResponse);
    }
    
    @PostMapping("/validate")
    public ApiResponse<ValidationResult> validateToken(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        ValidationResult result = tokenService.validateToken(token);
        return ApiResponse.success(result);
    }
    
    @PostMapping("/revoke")
    public ApiResponse<Boolean> revokeToken(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        boolean revoked = tokenService.revokeToken(token);
        if (revoked) {
            return ApiResponse.success("Token 作废成功", true);
        } else {
            return ApiResponse.error(400, "Token 作废失败");
        }
    }
    
    @PostMapping("/revoke/user/{userId}")
    public ApiResponse<Boolean> revokeTokenByUserId(@PathVariable String userId) {
        boolean revoked = tokenService.revokeTokenByUserId(userId);
        if (revoked) {
            return ApiResponse.success("用户 Token 作废成功", true);
        } else {
            return ApiResponse.error(404, "未找到该用户的有效 Token");
        }
    }
    
    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
