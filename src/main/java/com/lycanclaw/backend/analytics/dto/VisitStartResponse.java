package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 页面访问开始响应。
 * 用于把后端生成的 visitId 返回给前端，供离开页面时结算停留时间。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "页面访问开始响应")
public record VisitStartResponse(
        @Schema(description = "访问记录 ID")
        String visitId
) {
}
