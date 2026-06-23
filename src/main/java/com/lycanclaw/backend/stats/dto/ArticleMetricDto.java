package com.lycanclaw.backend.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文章指标快照响应。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
@Schema(description = "文章浏览量和评论数快照")
public record ArticleMetricDto(
        @Schema(description = "规范化文章路径", example = "/thoughts/example.html")
        String path,
        @Schema(description = "浏览量", example = "42")
        int pageviewCount,
        @Schema(description = "评论数", example = "3")
        int commentCount
) {
}
