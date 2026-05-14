package com.lycanclaw.backend.music.dto;

public record MusicQueueItemDto(
        String queueId,
        String id,
        String name,
        String artist,
        String cover,
        String url,
        String source,
        int priority,
        String enqueuedAt
) {
}
