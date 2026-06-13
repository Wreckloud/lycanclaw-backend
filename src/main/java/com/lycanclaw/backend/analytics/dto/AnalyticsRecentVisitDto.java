package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 最近文章访问记录。
 * 用于管理端查看访客最近阅读了哪篇文章、停留多久以及阅读到什么位置。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "最近文章访问记录")
public record AnalyticsRecentVisitDto(
        String path,
        String title,
        String visitorId,
        String nickname,
        String region,
        long durationSeconds,
        int maxScrollPercent,
        String visitedAt
) {
}
