package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 催更统计摘要。
 * 用于后台首页展示催更总量、今日数量和访客排行。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "催更统计摘要")
public record EncouragementSummaryDto(
        @Schema(description = "总催更数")
        long total,
        @Schema(description = "今日催更数")
        long today,
        @Schema(description = "催更最多的访客")
        List<EncouragementVisitorMetricDto> topVisitors
) {
}
