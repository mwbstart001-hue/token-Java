package com.example.tokenservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRequest {
    
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    private String username;
}
