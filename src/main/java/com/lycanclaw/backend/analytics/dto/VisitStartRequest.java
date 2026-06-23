package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 页面访问开始请求。
 * 用于前端在进入公开页面时创建一次访问记录。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "页面访问开始请求")
public record VisitStartRequest(
        @Schema(description = "页面路径", example = "/thoughts/example.html")
        String path,
        @Schema(description = "页面标题", example = "一篇随想")
        String title,
        @Schema(description = "来源页面")
        String referrer,
        @Schema(description = "前端生成的匿名访客 ID")
        String visitorId
) {
}
