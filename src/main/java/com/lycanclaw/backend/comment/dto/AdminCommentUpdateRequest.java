package com.lycanclaw.backend.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理端评论更新请求。
 * 用于修改评论正文、审核状态或置顶状态。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "管理端评论更新请求")
public record AdminCommentUpdateRequest(
        @Schema(description = "评论纯文本正文，不修改时为空")
        String comment,
        @Schema(description = "审核状态：approved、waiting 或 spam")
        String status,
        @Schema(description = "是否置顶")
        Boolean sticky
) {
}
