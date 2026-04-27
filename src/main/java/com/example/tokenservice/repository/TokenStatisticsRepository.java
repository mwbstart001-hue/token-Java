package com.example.tokenservice.repository;

import com.example.tokenservice.model.TokenOperationType;
import com.example.tokenservice.model.TokenStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TokenStatisticsRepository extends JpaRepository<TokenStatistics, Long> {

    List<TokenStatistics> findByUserIdAndOperationTimeBetweenOrderByOperationTimeDesc(
            String userId, LocalDateTime startTime, LocalDateTime endTime);

    long countByUserIdAndOperationTypeAndOperationTimeBetween(
            String userId, TokenOperationType operationType, LocalDateTime startTime, LocalDateTime endTime);

    long countByUserIdAndOperationTypeAndSuccessIsTrueAndOperationTimeBetween(
            String userId, TokenOperationType operationType, LocalDateTime startTime, LocalDateTime endTime);

    long countByUserIdAndOperationTypeAndSuccessIsFalseAndOperationTimeBetween(
            String userId, TokenOperationType operationType, LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT DISTINCT s.userId FROM TokenStatistics s")
    List<String> findAllDistinctUserIds();

    long countByOperationTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    long countBySuccessIsTrueAndOperationTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT s.operationType, COUNT(s) FROM TokenStatistics s WHERE s.operationTime BETWEEN :startTime AND :endTime GROUP BY s.operationType")
    List<Object[]> countByOperationTypeBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Query("SELECT s.operationType, " +
           "COUNT(s), " +
           "SUM(CASE WHEN s.success = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN s.success = false THEN 1 ELSE 0 END) " +
           "FROM TokenStatistics s WHERE s.operationTime BETWEEN :startTime AND :endTime " +
           "GROUP BY s.operationType")
    List<Object[]> aggregateByOperationType(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Query("SELECT s.userId, s.operationType, COUNT(s), " +
           "SUM(CASE WHEN s.success = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN s.success = false THEN 1 ELSE 0 END) " +
           "FROM TokenStatistics s WHERE s.operationTime BETWEEN :startTime AND :endTime " +
           "GROUP BY s.userId, s.operationType")
    List<Object[]> aggregateByUserIdAndOperationType(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
