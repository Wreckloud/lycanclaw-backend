package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 访问统计数据质量摘要。
 * 用于后台总览提示 404、异常路径和 IP 地区库状态。
 * @author Wreckloud
 * @since 2026-07-07
 */
@Schema(description = "访问统计数据质量摘要")
public record AnalyticsDataQualityDto(
        @Schema(description = "统计窗口天数")
        int days,
        @Schema(description = "访问次数")
        long visits,
        @Schema(description = "独立访客数")
        long uniqueVisitors,
        @Schema(description = "404 访问次数")
        long notFoundVisits,
        @Schema(description = "疑似异常路径访问次数")
        long abnormalVisits,
        @Schema(description = "IP 地区库是否可用")
        boolean ipRegionAvailable
) {
}
