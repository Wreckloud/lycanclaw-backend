package com.lycanclaw.backend.game.dto;

/**
 * 在线对战 WebSocket 客户端消息。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public record GameClientMessage(
        String type,
        String roomId,
        String playerToken,
        String nickname,
        Integer bigIndex,
        Integer smallIndex,
        String text
) {
}
