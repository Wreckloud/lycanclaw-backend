package com.lycanclaw.backend.comment.controller;

import com.lycanclaw.backend.comment.dto.RecentCommentDto;
import com.lycanclaw.backend.comment.service.CommentService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评论接口控制器。
 * 用于提供评论相关 REST 接口。
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/comments")
@Tag(name = "评论", description = "最新评论接口")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "获取最新评论")
    @GetMapping("/recent")
    public ApiResponse<List<RecentCommentDto>> recent(
            @Parameter(description = "返回条数，默认 5，最大 20")
            @RequestParam(value = "limit", defaultValue = "5") int limit
    ) {
        return ApiResponse.ok(commentService.recentComments(limit));
    }

}
