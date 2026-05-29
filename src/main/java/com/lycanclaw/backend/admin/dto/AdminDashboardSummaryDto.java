package com.lycanclaw.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AdminDashboardSummaryDto：
 * AdminDashboardSummary的数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "管理端首页摘要响应")
public record AdminDashboardSummaryDto(
        @Schema(description = "汇总检查时间（ISO-8601，按系统时区）", example = "2026-05-28T22:05:43+08:00")
        String checkedAt,
        @Schema(description = "音乐登录状态摘要")
        AdminMusicStatusDto music,
        @Schema(description = "内容治理摘要")
        AdminGovernanceSummaryDto governance,
        @Schema(description = "风控配置摘要")
        AdminRiskControlSummaryDto riskControl,
        @Schema(description = "运维检查摘要")
        AdminOpsSummaryDto ops
) {
}
