package com.lycanclaw.backend.game.dto;

/**
 * 在线对战 WebSocket 服务端消息。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public record GameServerMessage(
        String type,
        GameRoomSnapshot data,
        String message
) {

    public static GameServerMessage snapshot(GameRoomSnapshot snapshot) {
        return new GameServerMessage("snapshot", snapshot, null);
    }

    public static GameServerMessage error(String message) {
        return new GameServerMessage("error", null, message);
    }
}
