package com.lycanclaw.backend.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 推荐文章数据传输对象
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "推荐文章")
public record RecommendationPostDto(
        @Schema(description = "文章链接", example = "/thoughts/我从人间走过.html")
        String url,
        @Schema(description = "文章标题", example = "我从人间走过")
        String title,
        @Schema(description = "文章摘要")
        String description,
        @Schema(description = "发布时间")
        String date,
        @Schema(description = "文章标签")
        List<String> tags,
        @Schema(description = "浏览量", example = "114")
        int pageviewCount,
        @Schema(description = "评论数", example = "3")
        int commentCount,
        @Schema(description = "热门分数", example = "186.0")
        double hotScore,
        @Schema(description = "是否手动置顶")
        boolean manualPinned
) {
}
