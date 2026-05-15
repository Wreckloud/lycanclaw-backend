package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @Description 单日贡献条目模型
 * @Author Wreckloud
 * @Date 2026-05-15
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
