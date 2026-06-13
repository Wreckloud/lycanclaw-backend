package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 最近收听记录。
 * 用于管理端查看访客、歌曲、来源和本次播放完成度。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "最近收听记录")
public record MusicListenRecordDto(
        String visitorId,
        String nickname,
        String avatar,
        String songId,
        String songName,
        String artist,
        String source,
        String urlSource,
        String pagePath,
        double progressPercent,
        boolean completed,
        String updatedAt
) {
}
