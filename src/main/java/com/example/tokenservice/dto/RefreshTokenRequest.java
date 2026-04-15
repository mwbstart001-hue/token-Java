package com.example.tokenservice.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RefreshTokenRequest {
    
    @NotBlank(message = "Refresh Token 不能为空")
    private String refreshToken;
}
