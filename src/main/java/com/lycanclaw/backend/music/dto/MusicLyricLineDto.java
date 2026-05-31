package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 歌词时间行模型。
 * 描述一行歌词的时间点与文本内容。
 * @author Wreckloud
 * @since 2026-05-31
 */
@Schema(description = "歌词时间行")
public record MusicLyricLineDto(
        @Schema(description = "歌词时间点（毫秒）", example = "63500")
        long timeMs,
        @Schema(description = "歌词文本", example = "我从人间走过")
        String text
) {
}
