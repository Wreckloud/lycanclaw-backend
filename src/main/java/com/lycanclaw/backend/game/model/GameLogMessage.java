package com.lycanclaw.backend.game.model;

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
        long createdAt
) {
}
