package com.lycanclaw.backend.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理员评论回复请求。
 * 用于在自有后台向指定 Waline 评论提交管理员回复。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "管理员评论回复请求")
public record AdminCommentReplyRequest(
        String comment,
        String url,
        String rootId
) {
}
