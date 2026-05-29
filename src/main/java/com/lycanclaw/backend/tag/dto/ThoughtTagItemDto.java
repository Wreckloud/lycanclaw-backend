package com.lycanclaw.backend.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ThoughtTagItemDto：
 * ThoughtTagItem的数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "标签计数项")
public record ThoughtTagItemDto(
        @Schema(description = "标签名", example = "反刍日志")
        String tag,
        @Schema(description = "文章数量", example = "12")
        int count
) {
}
