package com.example.tokenservice.repository;

import com.example.tokenservice.model.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, String> {

    Optional<Token> findByTokenValue(String tokenValue);

    boolean existsByTokenValue(String tokenValue);

    void deleteByTokenValue(String tokenValue);

    @Modifying
    @Query("UPDATE Token t SET t.status = :status, t.updatedAt = :updatedAt WHERE t.tokenValue = :tokenValue AND t.status = com.example.tokenservice.model.TokenStatus.ACTIVE")
    int updateStatusIfActive(@Param("tokenValue") String tokenValue,
                              @Param("status") com.example.tokenservice.model.TokenStatus status,
                              @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE Token t SET t.status = :status, t.updatedAt = :updatedAt WHERE t.tokenValue = :tokenValue")
    void updateStatus(@Param("tokenValue") String tokenValue,
                      @Param("status") com.example.tokenservice.model.TokenStatus status,
                      @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("DELETE FROM Token t WHERE t.expiresAt < :now")
    void deleteByExpiresAtBefore(@Param("now") LocalDateTime now);
}
