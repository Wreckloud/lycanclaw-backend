package com.lycanclaw.backend.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * ThoughtTagsResponseDto：
 * ThoughtTagsResponse的数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "随想标签列表")
public record ThoughtTagsResponseDto(
        @Schema(description = "标签集合")
        List<ThoughtTagItemDto> tags,
        @Schema(description = "标签总数", example = "8")
        int totalTags,
        @Schema(description = "参与统计的文章数", example = "94")
        int totalPosts
) {
}
