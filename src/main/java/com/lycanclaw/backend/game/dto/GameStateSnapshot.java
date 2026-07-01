package com.lycanclaw.backend.game.dto;

import com.lycanclaw.backend.game.model.GameRecordedMove;
import com.lycanclaw.backend.game.model.GameRuleEvent;

import java.util.List;

/**
 * 在线对战棋局状态快照。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public record GameStateSnapshot(
        int[] board,
        int[] smallBoardStatus,
        boolean[] smallBoardResolved,
        List<List<Integer>> smallBoardWinningLines,
        int currentPlayer,
        Integer nextBoard,
        int winner,
        List<Integer> bigBoardWinningLine,
        boolean isStarted,
        List<GameRecordedMove> moveHistory,
        List<GameRecordedMove> lastTurnMoves,
        List<GameRuleEvent> lastRuleEvents
) {
}
