package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 打断插入单曲请求。
 * 在已有播放流中临时插入一首歌，播放结束后回归随机流。
 * @author Wreckloud
 * @since 2026-05-30
 */
@Schema(description = "单曲打断播放请求")
public record InterruptSingleRequest(
        @Schema(description = "歌曲 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String songId,
        @Schema(description = "播放入口，只允许 article-embed 或 about-ranking")
        String source
) {
}
