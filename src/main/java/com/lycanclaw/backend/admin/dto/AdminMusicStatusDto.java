package com.lycanclaw.backend.admin.dto;

/**
 * 管理端音乐登录状态
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
public record AdminMusicStatusDto(
        boolean ok,
        boolean loggedIn,
        String nickname,
        String message
) {
}
