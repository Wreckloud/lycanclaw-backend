package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页催更结算响应。
 * 仅确认本轮增量已接收，不向前台返回后台总催更数。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "首页催更结算响应")
public record EncouragementSettleResponse(
        @Schema(description = "是否已接收")
        boolean accepted,
        @Schema(description = "本轮入库增量")
        int delta
) {
}
