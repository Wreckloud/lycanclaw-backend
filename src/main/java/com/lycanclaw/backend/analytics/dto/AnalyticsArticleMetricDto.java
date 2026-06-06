package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文章访问指标。
 * 用于后台展示文章访问量、独立访客、停留时间和催更归属统计。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "文章访问指标")
public record AnalyticsArticleMetricDto(
        @Schema(description = "页面路径")
        String path,
        @Schema(description = "页面标题")
        String title,
        @Schema(description = "访问次数")
        long visits,
        @Schema(description = "独立访客数")
        long uniqueVisitors,
        @Schema(description = "平均停留秒数")
        double averageDurationSeconds,
        @Schema(description = "总停留秒数")
        long totalDurationSeconds,
        @Schema(description = "催更次数")
        long encouragements
) {
}
