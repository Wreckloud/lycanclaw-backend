package com.lycanclaw.backend.waline.dto;

/**
 * Waline 用户摘要。
 * 用于后台用户管理列表，展示登录账号、头像、角色和常用资料。
 * @author Wreckloud
 * @since 2026-06-17
 */
public record AdminWalineUserDto(
        String id,
        String displayName,
        String email,
        String url,
        String avatar,
        String type,
        String label,
        String github,
        String qq,
        String createdAt,
        String updatedAt
) {
}
