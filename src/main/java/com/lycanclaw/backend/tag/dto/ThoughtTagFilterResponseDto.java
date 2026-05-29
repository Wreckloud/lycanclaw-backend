package com.lycanclaw.backend.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * ThoughtTagFilterResponseDto：
 * ThoughtTagFilterResponse的数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "标签筛选结果")
public record ThoughtTagFilterResponseDto(
        @Schema(description = "筛选标签")
        String tag,
        @Schema(description = "当前页", example = "1")
        int page,
        @Schema(description = "每页数量", example = "10")
        int pageSize,
        @Schema(description = "总条数", example = "24")
        int total,
        @Schema(description = "总页数", example = "3")
        int totalPages,
        @Schema(description = "文章列表")
        List<ThoughtPostSummaryDto> posts
) {
}
