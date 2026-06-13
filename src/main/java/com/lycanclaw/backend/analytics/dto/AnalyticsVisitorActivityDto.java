package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文章访客活动摘要。
 * 用于文章详情展示单个访客的访问次数、累计阅读和最大阅读进度。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "文章访客活动摘要")
public record AnalyticsVisitorActivityDto(
        String visitorId,
        String nickname,
        String avatar,
        String ip,
        String region,
        long visits,
        long totalDurationSeconds,
        int maxScrollPercent,
        String lastVisitedAt
) {
}
