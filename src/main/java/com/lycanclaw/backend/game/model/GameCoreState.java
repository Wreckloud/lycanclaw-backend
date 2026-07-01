package com.lycanclaw.backend.game.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.lycanclaw.backend.game.model.GameConstants.CELL_COUNT;
import static com.lycanclaw.backend.game.model.GameConstants.EMPTY;
import static com.lycanclaw.backend.game.model.GameConstants.X;

/**
 * 在线九宫叠阵核心棋局状态。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public class GameCoreState {

    private final int[] board = new int[CELL_COUNT];
    private final int[] smallBoardStatus = new int[9];
    private final boolean[] smallBoardResolved = new boolean[9];
    private final List<List<Integer>> smallBoardWinningLines = new ArrayList<>();
    private int currentPlayer = X;
    private Integer nextBoard;
    private int winner = EMPTY;
    private List<Integer> bigBoardWinningLine;
    private boolean started;
    private final List<GameRecordedMove> moveHistory = new ArrayList<>();
    private final List<GameRecordedMove> lastTurnMoves = new ArrayList<>();
    private final List<GameRuleEvent> lastRuleEvents = new ArrayList<>();

    public GameCoreState() {
        Arrays.fill(board, EMPTY);
        Arrays.fill(smallBoardStatus, EMPTY);
        for (int i = 0; i < 9; i++) {
            smallBoardWinningLines.add(null);
        }
    }

    public int[] board() {
        return board;
    }

    public int[] smallBoardStatus() {
        return smallBoardStatus;
    }

    public boolean[] smallBoardResolved() {
        return smallBoardResolved;
    }

    public List<List<Integer>> smallBoardWinningLines() {
        return smallBoardWinningLines;
    }

    public int currentPlayer() {
        return currentPlayer;
    }

    public void currentPlayer(int currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    public Integer nextBoard() {
        return nextBoard;
    }

    public void nextBoard(Integer nextBoard) {
        this.nextBoard = nextBoard;
    }

    public int winner() {
        return winner;
    }

    public void winner(int winner) {
        this.winner = winner;
    }

    public List<Integer> bigBoardWinningLine() {
        return bigBoardWinningLine;
    }

    public void bigBoardWinningLine(List<Integer> bigBoardWinningLine) {
        this.bigBoardWinningLine = bigBoardWinningLine;
    }

    public boolean started() {
        return started;
    }

    public void started(boolean started) {
        this.started = started;
    }

    public List<GameRecordedMove> moveHistory() {
        return moveHistory;
    }

    public List<GameRecordedMove> lastTurnMoves() {
        return lastTurnMoves;
    }

    public List<GameRuleEvent> lastRuleEvents() {
        return lastRuleEvents;
    }

}
