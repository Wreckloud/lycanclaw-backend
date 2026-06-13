package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 访客画像详情。
 * 汇总匿名或已关联访客的身份、地区、设备、访问、催更和收听行为。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "访客画像详情")
public record AnalyticsVisitorProfileDto(
        String visitorId,
        String nickname,
        String avatar,
        String provider,
        String ip,
        String region,
        String device,
        long visits,
        long totalDurationSeconds,
        long encouragements,
        long listenedSeconds,
        List<AnalyticsArticleMetricDto> topArticles,
        List<AnalyticsNamedMetricDto> referrers,
        List<AnalyticsNamedMetricDto> topSongs
) {
}
