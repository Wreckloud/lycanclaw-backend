package com.lycanclaw.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 管理端治理摘要
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "管理端治理摘要")
public record AdminGovernanceSummaryDto(
        @Schema(description = "治理模块是否正常", example = "true")
        boolean ok,
        @Schema(description = "治理状态说明", example = "")
        String message,
        @Schema(description = "手动推荐条目数", example = "3")
        int manualRecommendationCount,
        @Schema(description = "手动推荐更新时间", example = "2026-05-28T22:05:43+08:00")
        String manualRecommendationUpdatedAt,
        @Schema(description = "随想标签总数", example = "18")
        int thoughtTagCount,
        @Schema(description = "随想文章总数", example = "95")
        int thoughtPostCount,
        @Schema(description = "最新评论采样数量", example = "5")
        int recentCommentCount,
        @Schema(description = "同步状态等级（green/yellow/red）", example = "green")
        String syncLevel,
        @Schema(description = "同步状态检查时间", example = "2026-05-28T22:05:43+08:00")
        String syncCheckedAt,
        @Schema(description = "治理动作接口路径映射")
        Map<String, String> actions
) {
}
