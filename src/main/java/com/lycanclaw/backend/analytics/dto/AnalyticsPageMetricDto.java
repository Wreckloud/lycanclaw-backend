package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 普通页面访问指标。
 * 用于后台展示首页、列表、关于、项目和游戏等非文章页面的访问情况。
 *
 * @author Wreckloud
 * @since 2026-06-24
 */
@Schema(description = "普通页面访问指标")
public record AnalyticsPageMetricDto(
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
        @Schema(description = "最近访问时间")
        String lastVisitedAt
) {
}
