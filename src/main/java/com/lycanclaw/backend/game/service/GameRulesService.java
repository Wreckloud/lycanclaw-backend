package com.lycanclaw.backend.game.service;

import com.lycanclaw.backend.game.model.GameCoreState;
import com.lycanclaw.backend.game.model.GameMove;
import com.lycanclaw.backend.game.model.GameRecordedMove;
import com.lycanclaw.backend.game.model.GameRuleEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.lycanclaw.backend.game.model.GameConstants.BOARD_COUNT;
import static com.lycanclaw.backend.game.model.GameConstants.CENTER_INDEX;
import static com.lycanclaw.backend.game.model.GameConstants.DRAW;
import static com.lycanclaw.backend.game.model.GameConstants.EMPTY;
import static com.lycanclaw.backend.game.model.GameConstants.O;
import static com.lycanclaw.backend.game.model.GameConstants.X;

/**
 * 九宫叠阵在线对战规则服务。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
@Service
public class GameRulesService {

    private static final int[][] WINNING_LINES = {
            {0, 1, 2},
            {3, 4, 5},
            {6, 7, 8},
            {0, 3, 6},
            {1, 4, 7},
            {2, 5, 8},
            {0, 4, 8},
            {2, 4, 6}
    };

    public boolean canMove(GameCoreState state, int bigIndex, int smallIndex) {
        if (!state.started() || state.winner() != EMPTY) return false;
        if (!isValidIndex(bigIndex) || !isValidIndex(smallIndex)) return false;
        if (state.nextBoard() != null && state.nextBoard() != bigIndex) return false;
        if (state.board()[cellIndex(bigIndex, smallIndex)] != EMPTY) return false;
        return !isBoardFull(state, bigIndex);
    }

    public boolean applyMove(GameCoreState state, GameMove move) {
        if (!canMove(state, move.bigIndex(), move.smallIndex())) return false;

        int movePlayer = state.currentPlayer();
        state.lastRuleEvents().clear();
        state.lastTurnMoves().clear();
        state.board()[cellIndex(move.bigIndex(), move.smallIndex())] = movePlayer;

        GameRecordedMove manualMove = new GameRecordedMove(
                move.bigIndex(),
                move.smallIndex(),
                movePlayer,
                "manual",
                null
        );
        state.moveHistory().add(manualMove);
        state.lastTurnMoves().add(manualMove);

        claimSmallBoardIfNeeded(state, move.bigIndex());

        List<GameRuleEvent> settlementEvents = settleFullBoards(state, List.of(move.bigIndex()));
        state.lastRuleEvents().addAll(settlementEvents);
        for (GameRuleEvent event : settlementEvents) {
            state.lastTurnMoves().addAll(event.filledCells());
        }
        refreshBigBoardWinner(state);

        if (state.winner() != EMPTY) return true;

        GameRuleEvent directSettlement = settlementEvents.stream()
                .filter(event -> event.boardIndex() == move.bigIndex())
                .findFirst()
                .orElse(null);
        if (directSettlement != null && isPlayer(directSettlement.owner())) {
            state.nextBoard(null);
            state.currentPlayer(opponent(directSettlement.owner()));
            return true;
        }

        state.currentPlayer(opponent(movePlayer));

        if (move.smallIndex() == CENTER_INDEX || isBoardFull(state, move.smallIndex())) {
            state.nextBoard(null);
            return true;
        }

        state.nextBoard(move.smallIndex());
        return true;
    }

    public void resign(GameCoreState state, int loser) {
        state.winner(opponent(loser));
        state.bigBoardWinningLine(null);
        state.nextBoard(null);
    }

    public int opponent(int player) {
        return player == X ? O : X;
    }

    public boolean isPlayer(int value) {
        return value == X || value == O;
    }

    private List<GameRuleEvent> settleFullBoards(GameCoreState state, List<Integer> initialBoardIndexes) {
        List<GameRuleEvent> events = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>(initialBoardIndexes);

        while (!queue.isEmpty()) {
            int boardIndex = queue.pollFirst();
            if (state.smallBoardResolved()[boardIndex]) continue;
            if (!isBoardFull(state, boardIndex)) continue;

            claimSmallBoardIfNeeded(state, boardIndex);

            if (state.smallBoardStatus()[boardIndex] == EMPTY) {
                state.smallBoardStatus()[boardIndex] = DRAW;
                state.smallBoardWinningLines().set(boardIndex, null);
            }

            state.smallBoardResolved()[boardIndex] = true;
            int owner = state.smallBoardStatus()[boardIndex];

            if (!isPlayer(owner)) {
                events.add(new GameRuleEvent(boardIndex, DRAW, 0, List.of()));
                continue;
            }

            List<GameRecordedMove> filledCells = fillEntranceCells(state, boardIndex, owner);
            events.add(new GameRuleEvent(boardIndex, owner, filledCells.size(), filledCells));

            List<Integer> affectedBoardIndexes = filledCells.stream()
                    .map(GameRecordedMove::bigIndex)
                    .distinct()
                    .toList();
            for (int affectedBoardIndex : affectedBoardIndexes) {
                claimSmallBoardIfNeeded(state, affectedBoardIndex);
                if (isBoardFull(state, affectedBoardIndex) && !state.smallBoardResolved()[affectedBoardIndex]) {
                    queue.addLast(affectedBoardIndex);
                }
            }
        }

        return events;
    }

    private List<GameRecordedMove> fillEntranceCells(GameCoreState state, int targetBoardIndex, int owner) {
        List<GameRecordedMove> filledCells = new ArrayList<>();

        for (int bigIndex = 0; bigIndex < BOARD_COUNT; bigIndex++) {
            int cellIndex = cellIndex(bigIndex, targetBoardIndex);
            if (state.board()[cellIndex] != EMPTY) continue;

            state.board()[cellIndex] = owner;
            filledCells.add(new GameRecordedMove(
                    bigIndex,
                    targetBoardIndex,
                    owner,
                    "auto",
                    targetBoardIndex
            ));
        }

        return filledCells;
    }

    private void claimSmallBoardIfNeeded(GameCoreState state, int bigIndex) {
        if (state.smallBoardStatus()[bigIndex] != EMPTY) return;

        BoardResult result = getBoardResult(getBoardCells(state, bigIndex));
        if (isPlayer(result.status())) {
            state.smallBoardStatus()[bigIndex] = result.status();
            state.smallBoardWinningLines().set(bigIndex, result.winningLine());
        }
    }

    private void refreshBigBoardWinner(GameCoreState state) {
        BoardResult result = getBoardResult(state.smallBoardStatus());
        state.winner(result.status());
        state.bigBoardWinningLine(result.winningLine());
    }

    private int[] getBoardCells(GameCoreState state, int bigIndex) {
        int[] cells = new int[BOARD_COUNT];
        for (int smallIndex = 0; smallIndex < BOARD_COUNT; smallIndex++) {
            cells[smallIndex] = state.board()[cellIndex(bigIndex, smallIndex)];
        }
        return cells;
    }

    private BoardResult getBoardResult(int[] cells) {
        for (int[] line : WINNING_LINES) {
            int first = cells[line[0]];
            if (isPlayer(first) && first == cells[line[1]] && first == cells[line[2]]) {
                return new BoardResult(first, Arrays.stream(line).boxed().toList());
            }
        }

        for (int cell : cells) {
            if (cell == EMPTY) {
                return new BoardResult(EMPTY, null);
            }
        }

        return new BoardResult(DRAW, null);
    }

    private boolean isBoardFull(GameCoreState state, int bigIndex) {
        for (int smallIndex = 0; smallIndex < BOARD_COUNT; smallIndex++) {
            if (state.board()[cellIndex(bigIndex, smallIndex)] == EMPTY) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidIndex(int value) {
        return value >= 0 && value < BOARD_COUNT;
    }

    private int cellIndex(int bigIndex, int smallIndex) {
        return bigIndex * BOARD_COUNT + smallIndex;
    }

    private record BoardResult(int status, List<Integer> winningLine) {
    }
}
