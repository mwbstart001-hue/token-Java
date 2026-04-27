package com.example.tokenservice.command;

import com.example.tokenservice.dto.TokenInfo;
import com.example.tokenservice.model.Token;
import com.example.tokenservice.model.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 获取 Token 信息命令
 * 命令模式：封装获取 Token 信息的逻辑
 * 
 * 职责：
 * 1. 从存储中获取 Token
 * 2. 转换为 TokenInfo DTO
 * 3. 返回 Optional（不存在时为空）
 */
public class GetTokenInfoCommand implements TokenCommand<Optional<TokenInfo>> {

    private static final Logger log = LoggerFactory.getLogger(GetTokenInfoCommand.class);

    private final String tokenValue;
    private final TokenStore tokenStore;

    public GetTokenInfoCommand(String tokenValue, TokenStore tokenStore) {
        this.tokenValue = tokenValue;
        this.tokenStore = tokenStore;
    }

    @Override
    public Optional<TokenInfo> execute() {
        log.debug("获取 Token 信息");

        Optional<Token> tokenOpt = tokenStore.findByTokenValue(tokenValue);

        if (!tokenOpt.isPresent()) {
            log.warn("获取 Token 信息失败：Token 不存在");
            return Optional.empty();
        }

        Token token = tokenOpt.get();
        TokenInfo info = new TokenInfo();
        info.setTokenValue(token.getTokenValue());
        info.setUserId(token.getUserId());
        info.setSubject(token.getSubject());
        info.setIssuedAt(token.getIssuedAt());
        info.setExpiresAt(token.getExpiresAt());
        info.setStatus(token.getStatus());
        info.setValid(token.isValid());

        log.debug("获取 Token 信息成功 - userId: {}, valid: {}", token.getUserId(), token.isValid());
        return Optional.of(info);
    }

    @Override
    public TokenCommandType getType() {
        return TokenCommandType.GET_INFO;
    }
}
