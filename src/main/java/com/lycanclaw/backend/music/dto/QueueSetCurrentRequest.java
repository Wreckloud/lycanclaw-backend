package com.lycanclaw.backend.music.dto;

public record QueueSetCurrentRequest(
        String queueId,
        Boolean resumeCurrent
) {
}
