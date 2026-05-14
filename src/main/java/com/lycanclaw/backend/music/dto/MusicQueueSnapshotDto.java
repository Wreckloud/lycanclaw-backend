package com.lycanclaw.backend.music.dto;

import java.util.List;

public record MusicQueueSnapshotDto(
        MusicQueueItemDto current,
        int queueSize,
        List<MusicQueueItemDto> queue
) {
}
