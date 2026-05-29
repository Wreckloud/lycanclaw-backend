package com.lycanclaw.backend.common.security;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AdminAuthPrincipal：
 * 负责AdminAuthPrincipal相关的安全控制。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "管理员鉴权主体")
public record AdminAuthPrincipal(
        @Schema(description = "鉴权模式：static/session", example = "session")
        String mode,
        @Schema(description = "Waline 用户 ID", example = "1234567890abcdef")
        String userId,
        @Schema(description = "昵称", example = "Wreckloud")
        String nickname,
        @Schema(description = "邮箱", example = "1677820334@qq.com")
        String email,
        @Schema(description = "QQ 号", example = "1677820334")
        String qq,
        @Schema(description = "角色", example = "administrator")
        String role,
        @Schema(description = "会话过期时间（ISO8601）", example = "2026-05-28T23:59:59+08:00")
        String expiresAt
) {

    public static AdminAuthPrincipal staticToken() {
        return new AdminAuthPrincipal("static", "", "静态令牌管理员", "", "", "administrator", "");
    }
}
