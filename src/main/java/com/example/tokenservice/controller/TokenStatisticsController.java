package com.example.tokenservice.controller;

import com.example.tokenservice.dto.ApiResponse;
import com.example.tokenservice.dto.TokenStatisticsSummary;
import com.example.tokenservice.model.TokenStatistics;
import com.example.tokenservice.service.TokenStatisticsService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/token/statistics")
public class TokenStatisticsController {

    private final TokenStatisticsService statisticsService;

    public TokenStatisticsController(TokenStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<TokenStatisticsSummary>> getUserStatistics(
            @PathVariable("userId") String userId,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        
        LocalDateTime start = parseTime(startTime, LocalDateTime.now().minusDays(7));
        LocalDateTime end = parseTime(endTime, LocalDateTime.now());
        
        List<TokenStatisticsSummary> statistics = statisticsService.getUserStatistics(userId, start, end);
        return ApiResponse.success("获取用户统计成功", statistics);
    }

    @GetMapping("/all")
    public ApiResponse<List<TokenStatisticsSummary>> getAllStatistics(
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        
        LocalDateTime start = parseTime(startTime, LocalDateTime.now().minusDays(7));
        LocalDateTime end = parseTime(endTime, LocalDateTime.now());
        
        List<TokenStatisticsSummary> statistics = statisticsService.getAllStatistics(start, end);
        return ApiResponse.success("获取所有用户统计成功", statistics);
    }

    @GetMapping("/global")
    public ApiResponse<List<TokenStatisticsSummary>> getGlobalStatistics(
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        
        LocalDateTime start = parseTime(startTime, LocalDateTime.now().minusDays(7));
        LocalDateTime end = parseTime(endTime, LocalDateTime.now());
        
        List<TokenStatisticsSummary> statistics = statisticsService.getGlobalStatistics(start, end);
        return ApiResponse.success("获取全局统计成功", statistics);
    }

    @GetMapping("/records/{userId}")
    public ApiResponse<List<TokenStatistics>> getUserRecords(
            @PathVariable("userId") String userId,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        
        LocalDateTime start = parseTime(startTime, LocalDateTime.now().minusDays(7));
        LocalDateTime end = parseTime(endTime, LocalDateTime.now());
        
        List<TokenStatistics> records = statisticsService.getUserRecords(userId, start, end);
        return ApiResponse.success("获取用户操作记录成功", records);
    }

    private LocalDateTime parseTime(String timeStr, LocalDateTime defaultValue) {
        if (timeStr == null || timeStr.isEmpty()) {
            return defaultValue;
        }
        try {
            return LocalDateTime.parse(timeStr);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
