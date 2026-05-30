package com.lycanclaw.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理音乐状态数据模型。
 * 用于在管理音乐状态业务链路中传递结构化数据。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "管理端音乐登录状态")
public record AdminMusicStatusDto(
        @Schema(description = "音乐状态检查是否成功", example = "true")
        boolean ok,
        @Schema(description = "是否处于已登录状态", example = "true")
        boolean loggedIn,
        @Schema(description = "当前登录昵称", example = "Wreckloud")
        String nickname,
        @Schema(description = "状态说明", example = "已登录")
        String message
) {
}
