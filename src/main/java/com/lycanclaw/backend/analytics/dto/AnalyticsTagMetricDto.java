package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 标签关注指标。
 * 用于后台把文章访问数据按 posts.json 中的 tag 聚合。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Schema(description = "标签关注指标")
public record AnalyticsTagMetricDto(
        @Schema(description = "标签名称")
        String tag,
        @Schema(description = "访问次数")
        long visits,
        @Schema(description = "关联文章数")
        long articleCount,
        @Schema(description = "平均停留秒数")
        double averageDurationSeconds
) {
}
