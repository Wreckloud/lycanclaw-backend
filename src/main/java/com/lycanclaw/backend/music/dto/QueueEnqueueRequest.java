package com.lycanclaw.backend.music.dto;

public record QueueEnqueueRequest(
        String id,
        String source,
        Boolean insertFront,
        Boolean interruptCurrent,
        Boolean resumeCurrent,
        Integer priority,
        String level,
        String dedupeMode
) {
}
