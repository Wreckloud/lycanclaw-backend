package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 最近催更事件。
 * 用于后台展示最新几条催更结算记录。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "最近催更事件")
public record EncouragementEventDto(
        @Schema(description = "匿名访客 ID")
        String visitorId,
        @Schema(description = "来源 IP")
        String ip,
        @Schema(description = "本轮催更增量")
        int delta,
        @Schema(description = "归属路径")
        String path,
        @Schema(description = "归属标题")
        String title,
        @Schema(description = "创建时间")
        String createdAt
) {
}
