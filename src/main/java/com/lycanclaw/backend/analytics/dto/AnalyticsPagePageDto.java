package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 普通页面访问分页结果。
 * 用于后台按条件浏览非文章页面统计，并单独展示异常路径。
 * @author Wreckloud
 * @since 2026-07-07
 */
@Schema(description = "普通页面访问分页结果")
public record AnalyticsPagePageDto(
        @Schema(description = "页码")
        int page,
        @Schema(description = "每页条数")
        int pageSize,
        @Schema(description = "总条数")
        long total,
        @Schema(description = "总页数")
        int totalPages,
        @Schema(description = "统计窗口天数")
        int days,
        @Schema(description = "普通页面指标")
        List<AnalyticsPageMetricDto> items,
        @Schema(description = "异常路径指标")
        List<AnalyticsPageMetricDto> abnormalPages
) {
}
