package com.example.tokenservice.service;

import com.example.tokenservice.dto.TokenLinkNode;
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
     * AOP层已过滤无效操作，这里直接记录
     * 
     * @param userId 用户ID
     * @param jwtId JWT ID
     * @param parentJwtId 父Token的JWT ID（续签时使用）
     * @param tokenValue Token值
     * @param operationType 操作类型
     * @param success 操作是否成功
     * @param failureReason 失败原因
     * @param sourceIp 来源IP
     * @param userAgent 用户代理
     */
    @Transactional
    public void recordOperation(String userId, String jwtId, String parentJwtId, String tokenValue,
                                  TokenOperationType operationType, boolean success,
                                  String failureReason, String sourceIp, String userAgent) {
        log.debug("记录Token操作 - userId: {}, type: {}, jwtId: {}, parentJwtId: {}, success: {}", 
                userId, operationType, jwtId, parentJwtId, success);

        TokenStatistics statistics = new TokenStatistics();
        statistics.setUserId(userId);
        statistics.setJwtId(jwtId);
        statistics.setParentJwtId(parentJwtId);
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
     * 按操作类型分组统计
     */
    public List<TokenStatisticsSummary> getGlobalStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("查询全局统计 - startTime: {}, endTime: {}", startTime, endTime);

        List<TokenStatisticsSummary> summaries = new ArrayList<>();

        List<Object[]> results = statisticsRepository.aggregateByOperationType(startTime, endTime);
        for (Object[] row : results) {
            TokenOperationType type = (TokenOperationType) row[0];
            long total = ((Number) row[1]).longValue();
            long success = ((Number) row[2]).longValue();
            long failure = ((Number) row[3]).longValue();

            TokenStatisticsSummary summary = new TokenStatisticsSummary("GLOBAL", type, total, success, failure);
            summary.setStartTime(startTime);
            summary.setEndTime(endTime);
            summaries.add(summary);
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

    /**
     * 获取Token链路追踪
     * 以指定的jwtId为根节点，递归构建完整的Token生命周期链路
     * 
     * @param jwtId 根节点的JWT ID
     * @return Token链路树形结构，如果未找到则返回null
     */
    @Transactional(readOnly = true)
    public TokenLinkNode getTokenChain(String jwtId) {
        log.debug("查询Token链路 - jwtId: {}", jwtId);

        List<TokenStatistics> rootRecords = statisticsRepository.findByJwtId(jwtId);
        if (rootRecords == null || rootRecords.isEmpty()) {
            log.warn("未找到Token记录 - jwtId: {}", jwtId);
            return null;
        }

        TokenStatistics rootRecord = findRootRecord(rootRecords, jwtId);
        if (rootRecord == null) {
            log.warn("未找到有效的根节点记录 - jwtId: {}", jwtId);
            return null;
        }

        TokenLinkNode rootNode = buildLinkNode(rootRecord);
        buildChildrenChain(rootNode);

        log.debug("Token链路构建完成 - jwtId: {}", jwtId);
        return rootNode;
    }

    /**
     * 查找根节点记录
     * 优先选择 GENERATE 或 RENEW 类型的记录（因为这些是创建型操作）
     */
    private TokenStatistics findRootRecord(List<TokenStatistics> records, String jwtId) {
        TokenStatistics generateRecord = records.stream()
                .filter(r -> TokenOperationType.GENERATE == r.getOperationType())
                .findFirst()
                .orElse(null);
        if (generateRecord != null) {
            return generateRecord;
        }

        TokenStatistics renewRecord = records.stream()
                .filter(r -> TokenOperationType.RENEW == r.getOperationType())
                .findFirst()
                .orElse(null);
        if (renewRecord != null) {
            return renewRecord;
        }

        return records.get(0);
    }

    /**
     * 递归构建子节点链路
     */
    private void buildChildrenChain(TokenLinkNode parentNode) {
        List<TokenStatistics> childRecords = statisticsRepository.findByParentJwtIdOrderByOperationTimeAsc(parentNode.getJwtId());
        
        for (TokenStatistics childRecord : childRecords) {
            TokenLinkNode childNode = buildLinkNode(childRecord);
            parentNode.addChild(childNode);
            buildChildrenChain(childNode);
        }
    }

    /**
     * 将 TokenStatistics 转换为 TokenLinkNode
     */
    private TokenLinkNode buildLinkNode(TokenStatistics statistics) {
        TokenLinkNode node = new TokenLinkNode();
        node.setJwtId(statistics.getJwtId());
        node.setParentJwtId(statistics.getParentJwtId());
        node.setOperationType(statistics.getOperationType());
        node.setOperationTime(statistics.getOperationTime());
        node.setUserId(statistics.getUserId());
        node.setSuccess(statistics.isSuccess());
        node.setFailureReason(statistics.getFailureReason());
        return node;
    }
}
