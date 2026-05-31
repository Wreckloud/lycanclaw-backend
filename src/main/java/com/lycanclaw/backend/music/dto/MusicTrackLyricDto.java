package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 歌词响应模型。
 * 返回指定歌曲的原文歌词时间轴。
 * @author Wreckloud
 * @since 2026-05-31
 */
@Schema(description = "歌曲歌词响应")
public record MusicTrackLyricDto(
        @Schema(description = "歌曲 ID", example = "1952657890")
        String id,
        @Schema(description = "是否存在可用歌词", example = "true")
        boolean hasLyric,
        @Schema(description = "歌词行列表")
        List<MusicLyricLineDto> lines
) {
}
