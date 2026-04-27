package com.example.tokenservice.dto;

import javax.validation.constraints.NotBlank;

/**
 * Token续签请求DTO
 */
public class TokenRenewRequest {

    @NotBlank(message = "token 不能为空")
    private String token;

    private Long expireSeconds;

    private boolean invalidateOldToken = true;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(Long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public boolean isInvalidateOldToken() {
        return invalidateOldToken;
    }

    public void setInvalidateOldToken(boolean invalidateOldToken) {
        this.invalidateOldToken = invalidateOldToken;
    }
}
