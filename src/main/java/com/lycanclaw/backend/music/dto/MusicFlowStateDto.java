package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 音乐流状态快照。
 * 返回当前会话的播放模式和已经解析完成的当前歌曲。
 * @author Wreckloud
 * @since 2026-05-30
 */
@Schema(description = "当前播放流状态")
public record MusicFlowStateDto(
        @Schema(description = "播放模式")
        String mode,
        @Schema(description = "当前歌曲；空表示没有播放")
        MusicPlaybackItemDto current
) {
}
