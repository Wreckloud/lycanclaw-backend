package com.lycanclaw.backend.waline.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.common.security.AdminAuthConstants;
import com.lycanclaw.backend.waline.dto.AdminWalineUserDto;
import com.lycanclaw.backend.waline.dto.AdminWalineUserListDto;
import com.lycanclaw.backend.waline.dto.AdminWalineUserUpdateRequest;
import com.lycanclaw.backend.waline.dto.WalineImportResultDto;
import com.lycanclaw.backend.waline.service.AdminWalineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端 Waline 接口控制器。
 * 提供 Waline 用户管理和数据导入导出能力。
 * @author Wreckloud
 * @since 2026-06-17
 */
@RestController
@RequestMapping("/api/admin/waline")
@Tag(name = "管理端 Waline", description = "自有后台中的 Waline 用户和数据维护接口")
public class AdminWalineController {

    private final AdminWalineService adminWalineService;

    public AdminWalineController(AdminWalineService adminWalineService) {
        this.adminWalineService = adminWalineService;
    }

    @Operation(summary = "分页查询 Waline 用户")
    @GetMapping("/users")
    public ApiResponse<AdminWalineUserListDto> users(
            @RequestHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER) String adminToken,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "") String keyword
    ) {
        return ApiResponse.ok(adminWalineService.listUsers(adminToken, page, pageSize, keyword));
    }

    @Operation(summary = "更新 Waline 用户")
    @PutMapping("/users/{id}")
    public ApiResponse<AdminWalineUserDto> updateUser(
            @RequestHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER) String adminToken,
            @PathVariable String id,
            @RequestBody AdminWalineUserUpdateRequest request
    ) {
        return ApiResponse.ok(adminWalineService.updateUser(adminToken, id, request));
    }

    @Operation(summary = "更新 Waline 用户时缺少 ID")
    @PutMapping({"/users", "/users/"})
    public ApiResponse<Void> updateUserMissingId() {
        throw new IllegalArgumentException("用户 ID 不能为空");
    }

    @Operation(summary = "禁用 Waline 用户")
    @DeleteMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> deleteUser(
            @RequestHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER) String adminToken,
            @PathVariable String id
    ) {
        adminWalineService.deleteUser(adminToken, id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @Operation(summary = "禁用 Waline 用户时缺少 ID")
    @DeleteMapping({"/users", "/users/"})
    public ApiResponse<Void> deleteUserMissingId() {
        throw new IllegalArgumentException("用户 ID 不能为空");
    }

    @Operation(summary = "导出 Waline 数据")
    @GetMapping("/export")
    public ApiResponse<JsonNode> exportDatabase(
            @RequestHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER) String adminToken
    ) {
        return ApiResponse.ok(adminWalineService.exportDatabase(adminToken));
    }

    @Operation(summary = "向空数据库初始化导入 Waline 数据")
    @PostMapping("/import")
    public ApiResponse<WalineImportResultDto> importDatabase(
            @RequestHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER) String adminToken,
            @RequestBody JsonNode payload
    ) {
        return ApiResponse.ok(adminWalineService.importDatabase(adminToken, payload));
    }
}
