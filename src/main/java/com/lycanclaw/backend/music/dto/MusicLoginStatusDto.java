package com.lycanclaw.backend.music.dto;

/**
 * 音乐登录状态
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
public record MusicLoginStatusDto(
        boolean loggedIn,
        String message,
        String nickname,
        String userId,
        String avatarUrl
) {
}
