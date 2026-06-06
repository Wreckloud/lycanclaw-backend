package com.lycanclaw.backend.admin.controller;

import com.lycanclaw.backend.admin.service.AdminDashboardService;
import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.runtimeconfig.service.RuntimeConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 统一管理控制台接口。
 * 聚合控制台首屏需要的摘要数据和运行时配置。
 * @author Wreckloud
 * @since 2026-06-03
 */
@RestController
@RequestMapping("/api/admin/console")
@Tag(name = "统一管理控制台", description = "后台控制台聚合数据")
public class AdminConsoleController {

    private final AdminDashboardService adminDashboardService;
    private final RuntimeConfigService runtimeConfigService;
    private final AppTimeProvider appTimeProvider;

    public AdminConsoleController(
            AdminDashboardService adminDashboardService,
            RuntimeConfigService runtimeConfigService,
            AppTimeProvider appTimeProvider
    ) {
        this.adminDashboardService = adminDashboardService;
        this.runtimeConfigService = runtimeConfigService;
        this.appTimeProvider = appTimeProvider;
    }

    @Operation(summary = "获取统一管理控制台摘要")
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(Map.of(
                "checkedAt", appTimeProvider.nowOffsetString(),
                "dashboard", adminDashboardService.buildSummary(),
                "runtimeConfig", runtimeConfigService.view()
        ));
    }
}
