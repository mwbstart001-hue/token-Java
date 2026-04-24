package com.example.tokenservice.config;

import com.example.tokenservice.exception.AuthenticationException;
import com.example.tokenservice.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * API密钥认证过滤器
 * 用于保护API端点，只有携带有效API密钥的请求才能通过
 * 可以通过配置启用/禁用
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private final TokenProperties tokenProperties;

    public ApiKeyAuthenticationFilter(TokenProperties tokenProperties) {
        this.tokenProperties = tokenProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        TokenProperties.ApiKey apiKeyConfig = tokenProperties.getApiKey();

        if (!apiKeyConfig.isEnabled()) {
            log.debug("API密钥认证已禁用，跳过认证");
            filterChain.doFilter(request, response);
            return;
        }

        String headerName = apiKeyConfig.getHeaderName();
        String apiKey = request.getHeader(headerName);

        log.debug("API密钥认证 - 请求路径: {}, 头名称: {}, 头值存在: {}",
                request.getRequestURI(), headerName, apiKey != null);

        if (!StringUtils.hasText(apiKey)) {
            log.warn("API密钥认证失败 - 缺少API密钥，请求路径: {}", request.getRequestURI());
            throw new AuthenticationException(ErrorCode.AUTH_MISSING_API_KEY);
        }

        if (!isValidApiKey(apiKey, apiKeyConfig.getAllowedKeys())) {
            log.warn("API密钥认证失败 - 无效的API密钥，请求路径: {}", request.getRequestURI());
            throw new AuthenticationException(ErrorCode.AUTH_INVALID_API_KEY);
        }

        log.debug("API密钥认证成功 - 请求路径: {}", request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    /**
     * 验证API密钥是否有效
     * @param apiKey 请求中的API密钥
     * @param allowedKeys 允许的API密钥列表
     * @return true表示有效
     */
    private boolean isValidApiKey(String apiKey, String[] allowedKeys) {
        if (allowedKeys == null || allowedKeys.length == 0) {
            log.warn("没有配置允许的API密钥");
            return false;
        }

        List<String> allowedKeyList = Arrays.asList(allowedKeys);
        return allowedKeyList.contains(apiKey);
    }
}
