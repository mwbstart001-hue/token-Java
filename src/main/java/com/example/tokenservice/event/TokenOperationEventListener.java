package com.example.tokenservice.event;

import com.example.tokenservice.service.TokenStatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Token操作事件监听器
 * 监听Token操作事件并记录统计
 * AOP层已过滤无效操作，这里直接处理
 */
@Component
public class TokenOperationEventListener {

    private static final Logger log = LoggerFactory.getLogger(TokenOperationEventListener.class);

    private final TokenStatisticsService statisticsService;

    public TokenOperationEventListener(TokenStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Async
    @EventListener
    public void handleTokenOperationEvent(TokenOperationEvent event) {
        log.debug("接收到Token操作事件 - userId: {}, type: {}, jwtId: {}, parentJwtId: {}, success: {}", 
                event.getUserId(), event.getOperationType(), event.getJwtId(), event.getParentJwtId(), event.isSuccess());

        try {
            statisticsService.recordOperation(
                    event.getUserId(),
                    event.getJwtId(),
                    event.getParentJwtId(),
                    event.getTokenValue(),
                    event.getOperationType(),
                    event.isSuccess(),
                    event.getFailureReason(),
                    event.getSourceIp(),
                    event.getUserAgent()
            );

            log.debug("Token操作统计记录完成 - userId: {}, type: {}", 
                    event.getUserId(), event.getOperationType());
        } catch (Exception e) {
            log.error("记录Token统计失败: {}", e.getMessage(), e);
        }
    }
}
