package com.lycanclaw.backend.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建在线对战房间响应。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
@Schema(description = "创建在线对战房间响应")
public record CreateGameRoomResponse(
        @Schema(description = "房间 ID", example = "A1B2C3")
        String roomId,
        @Schema(description = "玩家重连令牌")
        String playerToken
) {
}
