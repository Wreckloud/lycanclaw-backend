package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Waline 访客身份关联请求。
 * 用于提交匿名 visitorId 和当前 Waline token，由后端验证后保存安全资料。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "Waline 访客身份关联请求")
public record WalineIdentityLinkRequest(
        @Schema(description = "前端匿名访客 ID")
        String visitorId,
        @Schema(description = "Waline 登录 token，仅用于本次服务端验证")
        String walineToken
) {
}
