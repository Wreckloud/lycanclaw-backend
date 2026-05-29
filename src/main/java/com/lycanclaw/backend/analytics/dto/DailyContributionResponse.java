package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DailyContributionResponse：
 * DailyContributionResponse的数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "日贡献统计响应")
public record DailyContributionResponse(
        @Schema(description = "生成时间（ISO-8601）")
        String generatedAt,
        @Schema(description = "统计时区")
        String timezone,
        @Schema(description = "统计口径")
        String metric,
        @Schema(description = "统计天数")
        int days,
        @Schema(description = "统计目录列表")
        List<String> scope,
        @Schema(description = "按天数据")
        List<DailyContributionItem> data
) {
}
