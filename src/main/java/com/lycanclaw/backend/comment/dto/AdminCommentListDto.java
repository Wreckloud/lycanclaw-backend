package com.lycanclaw.backend.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 管理端评论分页结果。
 * 用于返回评论列表、分页信息和待审核数量。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "管理端评论分页结果")
public record AdminCommentListDto(
        @Schema(description = "当前页码")
        int page,
        @Schema(description = "总页数")
        int totalPages,
        @Schema(description = "全部评论数")
        int totalCount,
        @Schema(description = "已通过评论数")
        int approvedCount,
        @Schema(description = "待审核评论数")
        int waitingCount,
        @Schema(description = "垃圾评论数")
        int spamCount,
        @Schema(description = "评论列表")
        List<AdminCommentItemDto> data
) {
}
