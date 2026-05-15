package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * @Description 队列快照模型
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Schema(description = "播放队列快照")
public record MusicQueueSnapshotDto(
        @Schema(description = "当前播放项")
        MusicQueueItemDto current,
        @Schema(description = "等待队列总长度")
        int queueSize,
        @Schema(description = "返回的队列明细")
        List<MusicQueueItemDto> queue
) {
}
