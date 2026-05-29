package com.lycanclaw.backend.admin.controller;

import com.lycanclaw.backend.admin.dto.AdminDashboardSummaryDto;
import com.lycanclaw.backend.admin.service.AdminDashboardService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接口控制器。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "管理端首页", description = "管理端首页摘要数据")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @Operation(summary = "获取管理端首页摘要")
    @GetMapping("/summary")
    public ApiResponse<AdminDashboardSummaryDto> summary() {
        return ApiResponse.ok(adminDashboardService.buildSummary());
    }
}
