package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 催更统计摘要。
 * 用于后台首页和催更接口展示总量、趋势、访客排行与最近事件。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "催更统计摘要")
public record EncouragementSummaryDto(
        @Schema(description = "总催更数")
        long total,
        @Schema(description = "今日催更数")
        long today,
        @Schema(description = "近 7 天催更数")
        long week,
        @Schema(description = "催更最多的访客")
        List<EncouragementVisitorMetricDto> topVisitors,
        @Schema(description = "催更最多的页面")
        List<AnalyticsArticleMetricDto> topPages,
        @Schema(description = "最近催更事件")
        List<EncouragementEventDto> recent
) {
}
