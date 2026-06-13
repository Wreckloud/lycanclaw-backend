package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 歌曲收听指标。
 * 用于按歌曲展示播放次数、听众和播放完成度。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "歌曲收听指标")
public record MusicSongMetricDto(
        String songId,
        String songName,
        String artist,
        long plays,
        long uniqueListeners,
        double averageProgressPercent,
        double completionRate
) {
}
