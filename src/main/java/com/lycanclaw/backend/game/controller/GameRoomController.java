package com.lycanclaw.backend.game.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.game.dto.CreateGameRoomRequest;
import com.lycanclaw.backend.game.dto.CreateGameRoomResponse;
import com.lycanclaw.backend.game.service.GameRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线对战房间接口。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
@RestController
@Tag(name = "在线对战房间", description = "九宫叠阵在线对战房间接口")
public class GameRoomController {

    private final GameRoomService gameRoomService;

    public GameRoomController(GameRoomService gameRoomService) {
        this.gameRoomService = gameRoomService;
    }

    @PostMapping("/api/game/rooms")
    @Operation(summary = "创建在线对战房间", description = "创建房间并返回创建者的重连令牌")
    public ApiResponse<CreateGameRoomResponse> createRoom(@RequestBody CreateGameRoomRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return ApiResponse.ok(gameRoomService.createRoom(request.nickname()));
    }
}
