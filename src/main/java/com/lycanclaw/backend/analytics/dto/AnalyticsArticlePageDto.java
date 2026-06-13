package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 文章洞察分页结果。
 * 用于管理端按标题搜索、排序并分页浏览文章统计。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "文章洞察分页结果")
public record AnalyticsArticlePageDto(
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<AnalyticsArticleMetricDto> items,
        List<AnalyticsRecentVisitDto> recentVisits
) {
}
