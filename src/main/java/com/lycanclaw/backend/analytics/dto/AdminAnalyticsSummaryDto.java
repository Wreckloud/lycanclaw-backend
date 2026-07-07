package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 统计分析首页摘要。
 * 用于后台首页集中展示访问趋势、热门文章、普通页面、标签关注和催更统计。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "统计分析首页摘要")
public record AdminAnalyticsSummaryDto(
        @Schema(description = "统计窗口天数")
        int days,
        @Schema(description = "访问次数")
        long visits,
        @Schema(description = "独立访客数")
        long uniqueVisitors,
        @Schema(description = "平均停留秒数")
        double averageDurationSeconds,
        @Schema(description = "访问趋势")
        List<AnalyticsTrendPointDto> trend,
        @Schema(description = "热门文章")
        List<AnalyticsArticleMetricDto> topArticles,
        @Schema(description = "普通页面访问排行")
        List<AnalyticsPageMetricDto> topPages,
        @Schema(description = "标签关注")
        List<AnalyticsTagMetricDto> topTags,
        @Schema(description = "催更摘要")
        EncouragementSummaryDto encouragement,
        @Schema(description = "数据质量摘要")
        AnalyticsDataQualityDto dataQuality
) {
}
