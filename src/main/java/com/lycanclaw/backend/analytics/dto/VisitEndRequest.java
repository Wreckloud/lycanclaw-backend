package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 页面访问结束请求。
 * 用于前端在切页或关闭页面时提交本次访问的有效停留时长。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "页面访问结束请求")
public record VisitEndRequest(
        @Schema(description = "访问记录 ID")
        String visitId,
        @Schema(description = "有效停留毫秒数", example = "120000")
        Long durationMs
) {
}
