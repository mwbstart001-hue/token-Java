package com.example.tokenservice.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class BatchGenerateRequest {

    @NotBlank(message = "userId 不能为空")
    private String userId;

    private String subject;

    private Long expireSeconds;

    @Min(value = 1, message = "count 必须大于 0")
    @Max(value = 50, message = "count 不能超过 50")
    private int count = 1;

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

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
