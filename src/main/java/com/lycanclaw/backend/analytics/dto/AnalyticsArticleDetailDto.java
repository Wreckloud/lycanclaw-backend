package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 文章洞察详情。
 * 聚合文章核心指标、访问趋势、来源分布和访客活动。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "文章洞察详情")
public record AnalyticsArticleDetailDto(
        AnalyticsArticleMetricDto metric,
        List<AnalyticsTrendPointDto> trend,
        List<AnalyticsNamedMetricDto> referrers,
        List<AnalyticsVisitorActivityDto> visitors
) {
}
