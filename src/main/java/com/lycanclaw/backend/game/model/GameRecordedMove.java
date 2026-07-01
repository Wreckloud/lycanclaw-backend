package com.lycanclaw.backend.game.model;

/**
 * 九宫叠阵已记录落子。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public record GameRecordedMove(
        int bigIndex,
        int smallIndex,
        int player,
        String source,
        Integer settlementBoardIndex
) {
}
