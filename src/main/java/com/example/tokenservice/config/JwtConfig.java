package com.example.tokenservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    
    private String secret;
    private long expiration;
    private long accessTokenExpiration = 7200000;
    private long refreshTokenExpiration = 604800000;
}
