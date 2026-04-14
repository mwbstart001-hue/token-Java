package com.example.token.controller;

import com.example.token.dto.Result;
import com.example.token.model.TokenInfo;
import com.example.token.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/token")
public class TokenController {

    @Autowired
    private TokenService tokenService;

    @PostMapping("/generate")
    public Result<Map<String, Object>> generateToken(
            @RequestParam String userId,
            @RequestParam(required = false) Long expireSeconds) {
        TokenInfo tokenInfo = tokenService.generateToken(userId, expireSeconds);
        Map<String, Object> result = new HashMap<>();
        result.put("token", tokenInfo.getToken());
        result.put("userId", tokenInfo.getUserId());
        result.put("createTime", tokenInfo.getCreateTime());
        result.put("expireTime", tokenInfo.getExpireTime());
        return Result.success(result);
    }

    @GetMapping("/validate")
    public Result<Map<String, Object>> validateToken(@RequestParam String token) {
        Optional<TokenInfo> tokenInfoOpt = tokenService.validateToken(token);
        if (tokenInfoOpt.isPresent()) {
            TokenInfo tokenInfo = tokenInfoOpt.get();
            Map<String, Object> result = new HashMap<>();
            result.put("valid", true);
            result.put("token", tokenInfo.getToken());
            result.put("userId", tokenInfo.getUserId());
            result.put("createTime", tokenInfo.getCreateTime());
            result.put("expireTime", tokenInfo.getExpireTime());
            return Result.success(result);
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("valid", false);
            return Result.success(result);
        }
    }

    @PostMapping("/invalidate")
    public Result<Map<String, Object>> invalidateToken(@RequestParam String token) {
        boolean success = tokenService.invalidateToken(token);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        if (success) {
            result.put("message", "Token 已作废");
        } else {
            result.put("message", "Token 不存在或已作废");
        }
        return Result.success(result);
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> getTokenInfo(@RequestParam String token) {
        Optional<TokenInfo> tokenInfoOpt = tokenService.getTokenInfo(token);
        if (tokenInfoOpt.isPresent()) {
            TokenInfo tokenInfo = tokenInfoOpt.get();
            Map<String, Object> result = new HashMap<>();
            result.put("token", tokenInfo.getToken());
            result.put("userId", tokenInfo.getUserId());
            result.put("createTime", tokenInfo.getCreateTime());
            result.put("expireTime", tokenInfo.getExpireTime());
            result.put("valid", tokenInfo.isValid());
            result.put("expired", tokenInfo.isExpired());
            return Result.success(result);
        } else {
            return Result.error(404, "Token 不存在");
        }
    }
}
