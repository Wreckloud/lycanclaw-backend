package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 通用名称统计项。
 * 用于管理端展示来源、歌曲、播放入口等简单分布。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "通用名称统计项")
public record AnalyticsNamedMetricDto(
        String name,
        long value,
        long secondaryValue
) {
}
