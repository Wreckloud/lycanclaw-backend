package com.lycanclaw.backend.game.dto;

import com.lycanclaw.backend.game.model.GameLogMessage;
import com.lycanclaw.backend.game.model.GameRoomStatus;

import java.util.List;

/**
 * 在线对战房间快照。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public record GameRoomSnapshot(
        String roomId,
        GameRoomStatus roomStatus,
        Integer selfSide,
        List<GamePlayerSnapshot> players,
        GameStateSnapshot state,
        List<GameLogMessage> messages
) {
}
