package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 已验证访客身份摘要。
 * 用于返回关联后的昵称、头像和登录来源，不包含 Waline token。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "已验证访客身份摘要")
public record VisitorIdentityDto(
        String visitorId,
        String walineUserId,
        String nickname,
        String avatar,
        String provider,
        String updatedAt
) {
}
