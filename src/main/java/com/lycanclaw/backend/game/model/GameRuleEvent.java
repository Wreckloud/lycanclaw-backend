package com.lycanclaw.backend.game.model;

import java.util.List;

/**
 * 九宫叠阵满盘结算事件。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public record GameRuleEvent(
        int boardIndex,
        int owner,
        int filledCount,
        List<GameRecordedMove> filledCells
) {
}
