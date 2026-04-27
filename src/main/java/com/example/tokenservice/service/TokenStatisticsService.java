package com.example.tokenservice.service;

import com.example.tokenservice.dto.TokenStatisticsSummary;
import com.example.tokenservice.model.TokenOperationType;
import com.example.tokenservice.model.TokenStatistics;
import com.example.tokenservice.repository.TokenStatisticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Token统计服务
 * 提供Token操作的记录、查询和统计功能
 * 所有操作保证线程安全
 */
@Service
public class TokenStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(TokenStatisticsService.class);

    private final TokenStatisticsRepository statisticsRepository;

    public TokenStatisticsService(TokenStatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }

    /**
     * 记录Token操作
     * 无效Token操作不触发计数
     * 
     * @param userId 用户ID
     * @param jwtId JWT ID
     * @param tokenValue Token值
     * @param operationType 操作类型
     * @param success 操作是否成功
     * @param failureReason 失败原因
     * @param sourceIp 来源IP
     * @param userAgent 用户代理
     */
    @Transactional
    public void recordOperation(String userId, String jwtId, String tokenValue,
                                  TokenOperationType operationType, boolean success,
                                  String failureReason, String sourceIp, String userAgent) {
        if (!success && failureReason != null) {
            log.debug("操作失败，不触发统计计数 - userId: {}, type: {}, reason: {}", 
                    userId, operationType, failureReason);
            return;
        }

        log.debug("记录Token操作 - userId: {}, type: {}, success: {}", userId, operationType, success);

        TokenStatistics statistics = new TokenStatistics();
        statistics.setUserId(userId);
        statistics.setJwtId(jwtId);
        statistics.setOperationType(operationType);
        statistics.setSuccess(success);
        statistics.setFailureReason(failureReason);
        statistics.setSourceIp(sourceIp);
        statistics.setUserAgent(userAgent);

        statisticsRepository.save(statistics);

        log.debug("Token操作记录完成 - userId: {}, type: {}", userId, operationType);
    }

    /**
     * 获取指定用户的统计摘要
     */
    public List<TokenStatisticsSummary> getUserStatistics(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("查询用户统计 - userId: {}, startTime: {}, endTime: {}", userId, startTime, endTime);

        List<TokenStatisticsSummary> summaries = new ArrayList<>();

        for (TokenOperationType type : TokenOperationType.values()) {
            long total = statisticsRepository.countByUserIdAndOperationTypeAndOperationTimeBetween(
                    userId, type, startTime, endTime);
            long success = statisticsRepository.countByUserIdAndOperationTypeAndSuccessIsTrueAndOperationTimeBetween(
                    userId, type, startTime, endTime);
            long failure = statisticsRepository.countByUserIdAndOperationTypeAndSuccessIsFalseAndOperationTimeBetween(
                    userId, type, startTime, endTime);

            if (total > 0) {
                TokenStatisticsSummary summary = new TokenStatisticsSummary(userId, type, total, success, failure);
                summary.setStartTime(startTime);
                summary.setEndTime(endTime);
                summaries.add(summary);
            }
        }

        return summaries;
    }

    /**
     * 获取所有用户的统计摘要
     */
    public List<TokenStatisticsSummary> getAllStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("查询所有用户统计 - startTime: {}, endTime: {}", startTime, endTime);

        List<TokenStatisticsSummary> summaries = new ArrayList<>();

        List<Object[]> results = statisticsRepository.aggregateByUserIdAndOperationType(startTime, endTime);
        for (Object[] row : results) {
            String userId = (String) row[0];
            TokenOperationType type = (TokenOperationType) row[1];
            long total = ((Number) row[2]).longValue();
            long success = ((Number) row[3]).longValue();
            long failure = ((Number) row[4]).longValue();

            TokenStatisticsSummary summary = new TokenStatisticsSummary(userId, type, total, success, failure);
            summary.setStartTime(startTime);
            summary.setEndTime(endTime);
            summaries.add(summary);
        }

        return summaries;
    }

    /**
     * 获取全局统计
     */
    public List<TokenStatisticsSummary> getGlobalStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("查询全局统计 - startTime: {}, endTime: {}", startTime, endTime);

        List<TokenStatisticsSummary> summaries = new ArrayList<>();

        for (TokenOperationType type : TokenOperationType.values()) {
            long total = statisticsRepository.countByOperationTimeBetween(startTime, endTime);
            long success = statisticsRepository.countBySuccessIsTrueAndOperationTimeBetween(startTime, endTime);
            long failure = total - success;

            if (total > 0) {
                TokenStatisticsSummary summary = new TokenStatisticsSummary("GLOBAL", type, total, success, failure);
                summary.setStartTime(startTime);
                summary.setEndTime(endTime);
                summaries.add(summary);
            }
        }

        return summaries;
    }

    /**
     * 获取指定用户的操作记录列表
     */
    public List<TokenStatistics> getUserRecords(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("查询用户操作记录 - userId: {}, startTime: {}, endTime: {}", userId, startTime, endTime);
        return statisticsRepository.findByUserIdAndOperationTimeBetweenOrderByOperationTimeDesc(userId, startTime, endTime);
    }
}
