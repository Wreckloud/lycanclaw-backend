package com.lycanclaw.backend.game.model;

/**
 * 九宫叠阵在线对战常量。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public final class GameConstants {

    public static final int EMPTY = 0;
    public static final int X = 1;
    public static final int O = 2;
    public static final int DRAW = 3;
    public static final int CENTER_INDEX = 4;
    public static final int BOARD_COUNT = 9;
    public static final int CELL_COUNT = 81;

    private GameConstants() {
    }
}
