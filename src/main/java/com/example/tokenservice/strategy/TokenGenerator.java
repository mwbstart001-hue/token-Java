package com.example.tokenservice.strategy;

import java.time.LocalDateTime;

/**
 * Token 生成策略接口
 * 策略模式：封装 Token 生成算法
 * 
 * 职责：
 * 1. 定义 Token 生成的标准接口
 * 2. 不同的实现可以使用不同的 JWT 库或签名算法
 * 3. 易于扩展和替换
 */
public interface TokenGenerator {

    /**
     * 生成 Token 结果
     */
    TokenGenerationResult generate(String userId, String subject, LocalDateTime issuedAt, LocalDateTime expiresAt);

    /**
     * 获取支持的算法名称
     */
    String getAlgorithm();
}
