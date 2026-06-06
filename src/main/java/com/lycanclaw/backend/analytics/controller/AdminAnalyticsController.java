package com.lycanclaw.backend.analytics.controller;

import com.lycanclaw.backend.analytics.dto.AdminAnalyticsSummaryDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticleMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsTagMetricDto;
import com.lycanclaw.backend.analytics.dto.EncouragementSummaryDto;
import com.lycanclaw.backend.analytics.service.AdminAnalyticsService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端写作洞察接口。
 * 提供访问趋势、文章排行、标签关注和催更统计数据。
 * @author Wreckloud
 * @since 2026-06-04
 */
@RestController
@RequestMapping("/api/admin/analytics")
@Tag(name = "管理端写作洞察", description = "后台访问分析与催更统计")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    public AdminAnalyticsController(AdminAnalyticsService adminAnalyticsService) {
        this.adminAnalyticsService = adminAnalyticsService;
    }

    @Operation(summary = "获取写作洞察总览")
    @GetMapping("/summary")
    public ApiResponse<AdminAnalyticsSummaryDto> summary(
            @Parameter(description = "统计天数") @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponse.ok(adminAnalyticsService.summary(days));
    }

    @Operation(summary = "获取文章访问指标")
    @GetMapping("/articles")
    public ApiResponse<List<AnalyticsArticleMetricDto>> articles(
            @Parameter(description = "统计天数") @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponse.ok(adminAnalyticsService.articleMetrics(days));
    }

    @Operation(summary = "获取标签关注指标")
    @GetMapping("/tags")
    public ApiResponse<List<AnalyticsTagMetricDto>> tags(
            @Parameter(description = "统计天数") @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponse.ok(adminAnalyticsService.tagMetrics(days));
    }

    @Operation(summary = "获取催更统计")
    @GetMapping("/encouragement")
    public ApiResponse<EncouragementSummaryDto> encouragement(
            @Parameter(description = "统计天数") @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponse.ok(adminAnalyticsService.encouragementSummary(days));
    }
}
