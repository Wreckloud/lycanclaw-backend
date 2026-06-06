package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 趋势图数据点。
 * 用于后台首页图表展示每日访问、访客和催更变化。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "趋势图数据点")
public record AnalyticsTrendPointDto(
        @Schema(description = "日期", example = "2026-06-04")
        String date,
        @Schema(description = "访问次数")
        long visits,
        @Schema(description = "独立访客数")
        long uniqueVisitors,
        @Schema(description = "催更次数")
        long encouragements
) {
}
