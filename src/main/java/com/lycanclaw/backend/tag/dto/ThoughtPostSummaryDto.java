package com.lycanclaw.backend.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * @Description 随想文章摘要
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Schema(description = "随想文章摘要")
public record ThoughtPostSummaryDto(
        @Schema(description = "文章路径", example = "/thoughts/我从人间走过.html")
        String url,
        @Schema(description = "文章标题")
        String title,
        @Schema(description = "文章描述")
        String description,
        @Schema(description = "发布日期")
        String date,
        @Schema(description = "标签列表")
        List<String> tags,
        @Schema(description = "文章摘录")
        String excerpt,
        @Schema(description = "预计阅读分钟数", example = "8")
        int readMinutes
) {
}
