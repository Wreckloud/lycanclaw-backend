package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 音乐歌曲条目。
 * 用于返回歌曲 ID、名称、歌手和封面。
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
