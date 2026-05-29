package com.lycanclaw.backend.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "当前管理员身份信息")
public record AdminAuthMeDto(
        @Schema(description = "是否已认证", example = "true")
        boolean authenticated,
        @Schema(description = "会话模式：session/static", example = "session")
        String mode,
        @Schema(description = "Waline 用户 ID", example = "6814ecf05857a3d9b7e96c2c")
        String userId,
        @Schema(description = "昵称", example = "Wreckloud")
        String nickname,
        @Schema(description = "邮箱", example = "1677820334@qq.com")
        String email,
        @Schema(description = "QQ 号", example = "1677820334")
        String qq,
        @Schema(description = "角色", example = "administrator")
        String role,
        @Schema(description = "会话过期时间", example = "2026-05-28T23:59:59+08:00")
        String expiresAt
) {
}
