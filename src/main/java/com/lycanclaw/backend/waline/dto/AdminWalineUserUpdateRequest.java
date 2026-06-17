package com.lycanclaw.backend.waline.dto;

/**
 * Waline 用户更新请求。
 * 用于后台修改用户昵称、邮箱、网址、头像、标签和用户类型。
 * @author Wreckloud
 * @since 2026-06-17
 */
public record AdminWalineUserUpdateRequest(
        String displayName,
        String email,
        String url,
        String avatar,
        String type,
        String label
) {
}
