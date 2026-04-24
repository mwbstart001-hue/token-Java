package com.example.tokenservice.config;

import com.example.tokenservice.dto.ApiResponse;
import com.example.tokenservice.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Spring Security配置类
 * 配置API密钥认证、会话管理等安全策略
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                          ObjectMapper objectMapper) {
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        log.info("配置Spring Security策略...");

        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .anyRequest().permitAll()
                .and()
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling()
                .authenticationEntryPoint((request, response, authException) -> {
                    log.warn("认证入口点被调用: {}", authException.getMessage());
                    handleAuthError(response, ErrorCode.AUTH_FAILED);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.warn("访问被拒绝: {}", accessDeniedException.getMessage());
                    handleAuthError(response, ErrorCode.AUTH_FAILED);
                });

        log.info("Spring Security配置完成");
    }

    /**
     * 处理认证错误，返回统一格式的JSON响应
     */
    private void handleAuthError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.error(errorCode);
        String json = objectMapper.writeValueAsString(apiResponse);

        response.getWriter().write(json);
        response.getWriter().flush();
    }
}
