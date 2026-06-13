package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 音乐收听分析摘要。
 * 用于管理端展示播放次数、听众、播放完成度及热门歌曲和来源。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "音乐收听分析摘要")
public record MusicAnalyticsSummaryDto(
        int days,
        long plays,
        long uniqueListeners,
        double averageProgressPercent,
        double completionRate,
        List<MusicSongMetricDto> topSongs,
        List<AnalyticsNamedMetricDto> sources,
        List<AnalyticsNamedMetricDto> urlSources,
        List<MusicListenRecordDto> recent
) {
}
