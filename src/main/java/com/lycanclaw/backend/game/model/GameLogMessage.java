package com.lycanclaw.backend.game.model;

import java.util.Map;

/**
 * 在线对战日志消息。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public record GameLogMessage(
        String id,
        String type,
        Integer player,
        Integer sender,
        String senderName,
        String text,
        String eventType,
        Map<String, Object> eventData,
        long createdAt
) {
}
