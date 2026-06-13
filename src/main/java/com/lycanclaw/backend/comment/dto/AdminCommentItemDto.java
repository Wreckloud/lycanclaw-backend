package com.lycanclaw.backend.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理端评论条目。
 * 用于展示 Waline 评论内容、审核状态和常用管理字段。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "管理端评论条目")
public record AdminCommentItemDto(
        @Schema(description = "Waline 评论 ID")
        String id,
        @Schema(description = "评论者昵称")
        String nick,
        @Schema(description = "评论者邮箱")
        String mail,
        @Schema(description = "评论者主页")
        String link,
        @Schema(description = "评论者头像")
        String avatar,
        @Schema(description = "评论正文")
        String comment,
        @Schema(description = "文章路径")
        String url,
        @Schema(description = "文章标题")
        String articleTitle,
        @Schema(description = "创建时间")
        String createdAt,
        @Schema(description = "审核状态")
        String status,
        @Schema(description = "是否置顶")
        boolean sticky,
        @Schema(description = "回复根评论 ID")
        String rid,
        @Schema(description = "浏览器信息")
        String browser,
        @Schema(description = "操作系统信息")
        String os,
        @Schema(description = "来源 IP")
        String ip,
        @Schema(description = "来源地区")
        String region,
        @Schema(description = "Waline 登录用户 ID")
        String userId,
        @Schema(description = "Waline 用户类型")
        String userType
) {
}
