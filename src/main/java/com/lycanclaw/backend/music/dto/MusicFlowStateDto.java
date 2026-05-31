package com.lycanclaw.backend.music.dto;

import java.util.List;

/**
 * 音乐流状态快照。
 * 返回当前会话的播放模式、当前歌曲和后续预览。
 * @author Wreckloud
 * @since 2026-05-30
 */
public record MusicFlowStateDto(
        String mode,
        MusicQueueItemDto current,
        int queueSize,
        List<MusicQueueItemDto> nextPreview
) {
}
