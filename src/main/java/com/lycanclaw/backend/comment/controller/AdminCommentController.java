package com.lycanclaw.backend.comment.controller;

import com.lycanclaw.backend.comment.dto.AdminCommentItemDto;
import com.lycanclaw.backend.comment.dto.AdminCommentListDto;
import com.lycanclaw.backend.comment.dto.AdminCommentBatchRequest;
import com.lycanclaw.backend.comment.dto.AdminCommentReplyRequest;
import com.lycanclaw.backend.comment.dto.AdminCommentUpdateRequest;
import com.lycanclaw.backend.comment.dto.WalineNotificationStatusDto;
import com.lycanclaw.backend.comment.service.AdminCommentService;
import com.lycanclaw.backend.comment.service.WalineNotificationStatusService;
import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.common.security.AdminAuthConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端评论接口控制器。
 * 提供 Waline 评论查询、审核、置顶和删除能力。
 * @author Wreckloud
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/api/admin/comments")
@Tag(name = "管理端评论", description = "自有后台中的 Waline 评论管理接口")
public class AdminCommentController {

    private final AdminCommentService adminCommentService;
    private final WalineNotificationStatusService notificationStatusService;

    public AdminCommentController(
            AdminCommentService adminCommentService,
            WalineNotificationStatusService notificationStatusService
    ) {
        this.adminCommentService = adminCommentService;
        this.notificationStatusService = notificationStatusService;
    }

    @Operation(summary = "分页查询评论")
    @GetMapping
    public ApiResponse<AdminCommentListDto> list(
            @RequestHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER) String adminToken,
            @Parameter(description = "审核状态：all、approved、waiting 或 spam")
            @RequestParam(defaultValue = "all") String status,
            @Parameter(description = "昵称、邮箱或评论关键词")
            @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码，从 1 开始")
            @RequestParam(defaultValue = "1") int page
    ) {
        return ApiResponse.ok(adminCommentService.list(adminToken, status, keyword, page));
    }

    @Operation(summary = "更新评论状态")
    @PutMapping("/{id}")
    public ApiResponse<AdminCommentItemDto> update(
            @RequestHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER) String adminToken,
            @PathVariable String id,
            @RequestBody AdminCommentUpdateRequest request
    ) {
        return ApiResponse.ok(adminCommentService.update(adminToken, id, request));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(
            @RequestHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER) String adminToken,
            @PathVariable String id
    ) {
        adminCommentService.delete(adminToken, id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @Operation(summary = "回复评论")
    @PostMapping("/{id}/reply")
    public ApiResponse<AdminCommentItemDto> reply(
            @RequestHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER) String adminToken,
            @PathVariable String id,
            @RequestBody AdminCommentReplyRequest request
    ) {
        return ApiResponse.ok(adminCommentService.reply(adminToken, id, request));
    }

    @Operation(summary = "批量操作评论")
    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> batch(
            @RequestHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER) String adminToken,
            @RequestBody AdminCommentBatchRequest request
    ) {
        return ApiResponse.ok(adminCommentService.batch(adminToken, request));
    }

    @Operation(summary = "获取邮件通知配置状态")
    @GetMapping("/notification")
    public ApiResponse<WalineNotificationStatusDto> notification() {
        return ApiResponse.ok(notificationStatusService.status());
    }
}
