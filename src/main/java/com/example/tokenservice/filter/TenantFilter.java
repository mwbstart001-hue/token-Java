package com.example.tokenservice.filter;

import com.example.tokenservice.common.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TenantFilter implements Filter {
    
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        String tenantId = httpRequest.getHeader(TENANT_ID_HEADER);
        if (!StringUtils.hasText(tenantId)) {
            tenantId = "default";
        }
        
        TenantContext.setTenantId(tenantId);
        
        log.debug("Tenant context set to: {}", tenantId);
        
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
