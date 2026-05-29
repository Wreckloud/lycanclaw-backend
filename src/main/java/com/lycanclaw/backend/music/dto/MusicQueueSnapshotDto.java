package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * MusicQueueSnapshotDto：
 * MusicQueueSnapshot的数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "播放队列快照")
public record MusicQueueSnapshotDto(
        @Schema(description = "当前播放项")
        MusicQueueItemDto current,
        @Schema(description = "等待队列总长度")
        int queueSize,
        @Schema(description = "下一批预览项（最多 3 首）")
        List<MusicQueueItemDto> nextPreview
) {
}
