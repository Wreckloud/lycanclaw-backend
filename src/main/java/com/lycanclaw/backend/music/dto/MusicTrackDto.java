package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "歌曲基础信息")
public record MusicTrackDto(
        @Schema(description = "歌曲 ID")
        String id,
        @Schema(description = "歌曲名")
        String name,
        @Schema(description = "歌手")
        String artist,
        @Schema(description = "封面 URL")
        String cover
) {
}
