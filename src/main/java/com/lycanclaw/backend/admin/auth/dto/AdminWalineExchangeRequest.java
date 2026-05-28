package com.lycanclaw.backend.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Waline 登录换取后端管理会话请求。
 *
 * @author Wreckloud
 * @since 2026-05-28
 */
@Schema(description = "Waline 登录换取后端管理会话请求")
public record AdminWalineExchangeRequest(
        @Schema(description = "Waline 登录 token", requiredMode = Schema.RequiredMode.REQUIRED)
        String walineToken
) {
}
