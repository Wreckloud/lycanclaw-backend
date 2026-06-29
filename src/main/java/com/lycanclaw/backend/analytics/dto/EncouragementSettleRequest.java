package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页催更结算请求。
 * 用于前端把一轮连续点击后的累计增量一次性提交给后端。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "首页催更结算请求")
public record EncouragementSettleRequest(
        @Schema(description = "本轮催更增量", example = "12")
        Integer delta,
        @Schema(description = "前端生成的匿名访客 ID")
        String visitorId
) {
}
