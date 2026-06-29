package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 音乐播放项。
 * 描述后端为当前播放会话解析完成的一首歌曲。
 *
 * @author Wreckloud
 * @since 2026-06-24
 */
@Schema(description = "当前播放歌曲")
public record MusicPlaybackItemDto(
        @Schema(description = "歌曲 ID")
        String id,
        @Schema(description = "歌曲名")
        String name,
        @Schema(description = "歌手")
        String artist,
        @Schema(description = "封面地址")
        String cover,
        @Schema(description = "播放地址")
        String url,
        @Schema(description = "播放入口")
        String source,
        @Schema(description = "播放地址来源")
        String urlSource
) {
}
