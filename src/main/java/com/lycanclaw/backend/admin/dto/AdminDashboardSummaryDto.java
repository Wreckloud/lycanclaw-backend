package com.lycanclaw.backend.admin.dto;

/**
 * 管理端首页摘要响应
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
public record AdminDashboardSummaryDto(
        String checkedAt,
        AdminMusicStatusDto music,
        AdminGovernanceSummaryDto governance,
        AdminRiskControlSummaryDto riskControl,
        AdminOpsSummaryDto ops
) {
}
