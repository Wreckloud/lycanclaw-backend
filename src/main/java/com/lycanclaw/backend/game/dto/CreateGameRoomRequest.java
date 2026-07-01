package com.lycanclaw.backend.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建在线对战房间请求。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
@Schema(description = "创建在线对战房间请求")
public record CreateGameRoomRequest(
        @Schema(description = "玩家昵称，1-16 字", example = "维克罗德")
        String nickname
) {
}
