package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 写作洞察首页摘要。
 * 用于后台首页集中展示访问趋势、热门文章、标签关注和催更统计。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "写作洞察首页摘要")
public record AdminAnalyticsSummaryDto(
        @Schema(description = "统计窗口天数")
        int days,
        @Schema(description = "访问次数")
        long visits,
        @Schema(description = "独立访客数")
        long uniqueVisitors,
        @Schema(description = "平均停留秒数")
        double averageDurationSeconds,
        @Schema(description = "总催更数")
        long totalEncouragements,
        @Schema(description = "访问趋势")
        List<AnalyticsTrendPointDto> trend,
        @Schema(description = "热门文章")
        List<AnalyticsArticleMetricDto> topArticles,
        @Schema(description = "高停留文章")
        List<AnalyticsArticleMetricDto> topDurationArticles,
        @Schema(description = "标签关注")
        List<AnalyticsTagMetricDto> topTags,
        @Schema(description = "催更摘要")
        EncouragementSummaryDto encouragement
) {
}
