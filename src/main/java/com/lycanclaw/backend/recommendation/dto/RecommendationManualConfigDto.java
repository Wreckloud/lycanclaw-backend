package com.lycanclaw.backend.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 推荐手动配置数据模型。
 * 用于在推荐手动配置业务链路中传递结构化数据。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "手动推荐配置")
public record RecommendationManualConfigDto(
        @Schema(description = "手动置顶文章 URL 列表")
        List<String> manualUrls,
        @Schema(description = "不参与自动补位的文章 URL 列表")
        List<String> excludedUrls,
        @Schema(description = "更新时间（ISO 字符串）", example = "2026-05-16T10:00:00+08:00")
        String updatedAt
) {
}
