package com.example.tokenservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    
    private String token;
    private String userId;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
}
