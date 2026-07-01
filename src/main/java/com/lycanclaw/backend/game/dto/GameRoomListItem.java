package com.lycanclaw.backend.game.dto;

import com.lycanclaw.backend.game.model.GameRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 在线对战房间列表项。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
@Schema(description = "在线对战房间列表项")
public record GameRoomListItem(
        @Schema(description = "房间 ID", example = "A1B2C3D4")
        String roomId,

        @Schema(description = "房间状态", example = "WAITING")
        GameRoomStatus roomStatus,

        @Schema(description = "当前人数", example = "2")
        int playerCount,

        @Schema(description = "最大人数", example = "4")
        int maxPlayerCount,

        @Schema(description = "当前准备人数", example = "1")
        int readyCount,

        @Schema(description = "是否可加入", example = "true")
        boolean joinable,

        @Schema(description = "是否可准备上场", example = "true")
        boolean playable,

        @Schema(description = "更新时间")
        Instant updatedAt
) {
}
