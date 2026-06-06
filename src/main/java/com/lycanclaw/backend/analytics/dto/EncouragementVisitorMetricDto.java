package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 催更访客指标。
 * 用于后台查看哪些访客或 IP 触发了较多催更。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "催更访客指标")
public record EncouragementVisitorMetricDto(
        @Schema(description = "匿名访客 ID")
        String visitorId,
        @Schema(description = "来源 IP")
        String ip,
        @Schema(description = "催更总数")
        long totalDelta,
        @Schema(description = "最近催更时间")
        String lastAt
) {
}
