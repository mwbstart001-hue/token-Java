package com.example.tokenservice.dto;

import javax.validation.constraints.NotBlank;

public class TokenGenerateRequest {

    @NotBlank(message = "userId 不能为空")
    private String userId;

    private String subject;

    private Long expireSeconds;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(Long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }
}
