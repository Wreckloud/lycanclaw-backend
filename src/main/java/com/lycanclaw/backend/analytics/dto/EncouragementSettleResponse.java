package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页催更结算响应。
 * 返回本轮实际入库增量，不向前台公开后台总催更数。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "首页催更结算响应")
public record EncouragementSettleResponse(
        @Schema(description = "本轮入库增量")
        int delta
) {
}
