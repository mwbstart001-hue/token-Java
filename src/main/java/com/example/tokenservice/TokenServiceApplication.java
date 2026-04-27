package com.example.tokenservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TokenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TokenServiceApplication.class, args);
    }
}
