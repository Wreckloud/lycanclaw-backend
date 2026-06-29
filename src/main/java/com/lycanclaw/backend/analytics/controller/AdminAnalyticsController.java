package com.lycanclaw.backend.analytics.controller;

import com.lycanclaw.backend.analytics.dto.AnalyticsArticleDetailDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticlePageDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsVisitorProfileDto;
import com.lycanclaw.backend.analytics.dto.MusicAnalyticsSummaryDto;
import com.lycanclaw.backend.analytics.service.AdminAnalyticsService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端统计分析接口。
 * 提供文章、访客和音乐明细；总览数据统一由管理控制台接口返回。
 * @author Wreckloud
 * @since 2026-06-04
 */
@RestController
@RequestMapping("/api/admin/analytics")
@Tag(name = "管理端统计分析", description = "后台文章、访客和音乐统计")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    public AdminAnalyticsController(AdminAnalyticsService adminAnalyticsService) {
        this.adminAnalyticsService = adminAnalyticsService;
    }

    @Operation(summary = "获取文章访问指标")
    @GetMapping("/articles")
    public ApiResponse<AnalyticsArticlePageDto> articles(
            @Parameter(description = "统计天数") @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "标题关键词") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "排序：visits、duration、completion、scroll")
            @RequestParam(defaultValue = "visits") String sort,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "12") int pageSize
    ) {
        return ApiResponse.ok(adminAnalyticsService.articleMetrics(days, keyword, sort, page, pageSize));
    }

    @Operation(summary = "获取文章洞察详情")
    @GetMapping("/article")
    public ApiResponse<AnalyticsArticleDetailDto> article(
            @Parameter(description = "文章路径") @RequestParam String path,
            @Parameter(description = "统计天数") @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponse.ok(adminAnalyticsService.articleDetail(days, path));
    }

    @Operation(summary = "获取访客画像")
    @GetMapping("/visitor")
    public ApiResponse<AnalyticsVisitorProfileDto> visitor(
            @Parameter(description = "匿名访客 ID") @RequestParam String visitorId,
            @Parameter(description = "统计天数") @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponse.ok(adminAnalyticsService.visitorProfile(days, visitorId));
    }

    @Operation(summary = "获取音乐收听分析")
    @GetMapping("/music")
    public ApiResponse<MusicAnalyticsSummaryDto> music(
            @Parameter(description = "统计天数") @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponse.ok(adminAnalyticsService.musicAnalytics(days));
    }
}
