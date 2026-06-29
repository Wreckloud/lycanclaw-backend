package com.lycanclaw.backend.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Waline管理员身份交换请求。
 * 接收Waline登录token，用于换取后端管理员会话。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "Waline 登录换取后端管理会话请求")
public record AdminWalineExchangeRequest(
        @Schema(description = "Waline 登录 token", requiredMode = Schema.RequiredMode.REQUIRED)
        String walineToken
) {
}
