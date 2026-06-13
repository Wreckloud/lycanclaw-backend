package com.lycanclaw.backend.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 评论批量操作请求。
 * 用于批量通过、待审核、标记垃圾或删除评论。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "评论批量操作请求")
public record AdminCommentBatchRequest(
        List<String> ids,
        String action
) {
}
