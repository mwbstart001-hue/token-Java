package com.example.tokenservice.command;

import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStatus;
import com.example.tokenservice.model.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 作废 Token 命令
 * 命令模式：封装 Token 作废的完整逻辑
 * 
 * 职责：
 * 1. 检查 Token 是否存在
 * 2. 检查 Token 状态（必须是 ACTIVE）
 * 3. 更新 Token 状态为 INVALIDATED
 */
public class InvalidateTokenCommand implements TokenCommand<Boolean> {

    private static final Logger log = LoggerFactory.getLogger(InvalidateTokenCommand.class);

    private final String tokenValue;
    private final TokenStore tokenStore;

    public InvalidateTokenCommand(String tokenValue, TokenStore tokenStore) {
        this.tokenValue = tokenValue;
        this.tokenStore = tokenStore;
    }

    @Override
    public Boolean execute() {
        log.info("尝试作废 Token");

        Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);

        if (!tokenOpt.isPresent()) {
            log.warn("作废 Token 失败：Token 不存在");
            return false;
        }

        Token token = tokenOpt.get();

        if (token.getStatus() != TokenStatus.ACTIVE) {
            log.warn("作废 Token 失败：Token 状态已为 {}", token.getStatus());
            return false;
        }

        boolean success = tokenStore.updateStatusIfActive(tokenValue, TokenStatus.INVALIDATED);
        if (success) {
            log.info("Token 作废成功 - userId: {}", token.getUserId());
        } else {
            log.warn("Token 作废失败：可能已被其他线程作废");
        }

        return success;
    }

    @Override
    public TokenCommandType getType() {
        return TokenCommandType.INVALIDATE;
    }
}
