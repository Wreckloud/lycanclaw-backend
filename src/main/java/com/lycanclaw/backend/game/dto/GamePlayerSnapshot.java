package com.lycanclaw.backend.game.dto;

/**
 * 在线对战玩家快照。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public record GamePlayerSnapshot(
        int side,
        String nickname,
        boolean connected,
        boolean ready
) {
}
