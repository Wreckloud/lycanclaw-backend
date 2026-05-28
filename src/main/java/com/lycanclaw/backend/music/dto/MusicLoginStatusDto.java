package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 音乐登录状态
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "网易云音乐登录状态")
public record MusicLoginStatusDto(
        @Schema(description = "是否已登录", example = "true")
        boolean loggedIn,
        @Schema(description = "登录状态说明", example = "已登录")
        String message,
        @Schema(description = "当前昵称", example = "Wreckloud")
        String nickname,
        @Schema(description = "当前用户ID", example = "123456789")
        String userId,
        @Schema(description = "头像地址", example = "https://p1.music.126.net/xxx.jpg")
        String avatarUrl
) {
}
