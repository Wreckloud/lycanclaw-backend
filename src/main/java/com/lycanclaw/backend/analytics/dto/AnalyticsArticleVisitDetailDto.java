package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 单次文章访问明细。
 * 用于后台文章详情查看访客进入时间、停留、进度和来源。
 * @author Wreckloud
 * @since 2026-07-07
 */
@Schema(description = "单次文章访问明细")
public record AnalyticsArticleVisitDetailDto(
        @Schema(description = "匿名访客 ID")
        String visitorId,
        @Schema(description = "访客展示名")
        String nickname,
        @Schema(description = "访客头像")
        String avatar,
        @Schema(description = "地区")
        String region,
        @Schema(description = "来源")
        String referrer,
        @Schema(description = "停留秒数")
        long durationSeconds,
        @Schema(description = "最大阅读进度")
        int maxScrollPercent,
        @Schema(description = "进入时间")
        String visitedAt
) {
}
