package com.example.tokenservice.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class TokenRequest {
    
    @NotBlank(message = "用户ID不能为空")
    private String  userId;
    
    private String  username;
    
    private String  password;
}
