package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 每日贡献项记录模型。
 * 用于承载每日贡献项相关数据。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "单日贡献条目")
public record DailyContributionItem(
        @Schema(description = "日期（yyyy-MM-dd）")
        String date,
        @Schema(description = "新增行数")
        int additions,
        @Schema(description = "删除行数")
        int deletions,
        @Schema(description = "总贡献（新增+删除）")
        int total
) {
}
