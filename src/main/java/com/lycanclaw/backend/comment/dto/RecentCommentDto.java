package com.lycanclaw.backend.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 最新评论摘要模型。
 * 用于前台最新评论模块展示，不暴露 Waline 原始响应结构。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "最新评论摘要")
public record RecentCommentDto(
        @Schema(description = "评论ID")
        String id,
        @Schema(description = "评论昵称")
        String nick,
        @Schema(description = "评论内容")
        String comment,
        @Schema(description = "文章链接")
        String url,
        @Schema(description = "文章路径")
        String path,
        @Schema(description = "评论时间")
        String createdAt
) {
}
