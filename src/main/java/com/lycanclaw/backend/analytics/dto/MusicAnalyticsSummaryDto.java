package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 音乐收听分析摘要。
 * 用于管理端展示播放次数、听众、播放完成度、热门歌曲和最近收听记录。
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
        List<MusicListenRecordDto> recent
) {
}
