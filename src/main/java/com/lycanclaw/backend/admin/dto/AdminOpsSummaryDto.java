package com.lycanclaw.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AdminOpsSummaryDto：
 * AdminOpsSummary的数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "管理端运维检查摘要")
public record AdminOpsSummaryDto(
        @Schema(description = "运维检查模块是否正常", example = "true")
        boolean ok,
        @Schema(description = "异常说明，正常时为空字符串", example = "")
        String message,
        @Schema(description = "运维检查时间", example = "2026-05-28T22:05:43+08:00")
        String checkedAt,
        @Schema(description = "服务探测详情（Waline/NCM）")
        Object services,
        @Schema(description = "同步状态详情（posts/manual config）")
        Object sync
) {
}
