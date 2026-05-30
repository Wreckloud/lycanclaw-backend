package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 音乐播放队列项。
 * 用于描述当前播放或等待播放的一首歌曲。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "队列中的歌曲项")
public record MusicQueueItemDto(
        @Schema(description = "队列项唯一 ID")
        String queueId,
        @Schema(description = "歌曲 ID")
        String id,
        @Schema(description = "歌曲名")
        String name,
        @Schema(description = "歌手")
        String artist,
        @Schema(description = "封面 URL")
        String cover,
        @Schema(description = "可播放 URL")
        String url,
        @Schema(description = "来源标识")
        String source,
        @Schema(description = "优先级")
        int priority,
        @Schema(description = "入队时间（ISO-8601）")
        String enqueuedAt
) {
}
