package com.lycanclaw.backend.game.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 九宫叠阵落子坐标。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
@Schema(description = "九宫叠阵落子坐标")
public record GameMove(
        @Schema(description = "大棋盘索引，0-8", example = "0")
        int bigIndex,
        @Schema(description = "小棋盘格子索引，0-8", example = "4")
        int smallIndex
) {
}
