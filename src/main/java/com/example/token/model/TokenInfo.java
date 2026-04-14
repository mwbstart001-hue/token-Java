package com.example.token.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class TokenInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
    private String userId;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private boolean valid;

    public TokenInfo() {
    }

    public TokenInfo(String token, String userId, LocalDateTime createTime, LocalDateTime expireTime) {
        this.token = token;
        this.userId = userId;
        this.createTime = createTime;
        this.expireTime = expireTime;
        this.valid = true;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expireTime);
    }
}
