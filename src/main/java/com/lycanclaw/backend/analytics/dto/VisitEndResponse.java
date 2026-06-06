package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 页面访问结束响应。
 * 用于确认后端最终保存的有效停留时长。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "页面访问结束响应")
public record VisitEndResponse(
        @Schema(description = "访问记录 ID")
        String visitId,
        @Schema(description = "后端保存的有效停留毫秒数")
        long durationMs
) {
}
