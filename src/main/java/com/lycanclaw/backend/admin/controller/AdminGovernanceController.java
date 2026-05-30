package com.lycanclaw.backend.admin.controller;

import com.lycanclaw.backend.admin.service.AdminGovernanceService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理治理接口控制器。
 * 用于提供管理治理相关 REST 接口。
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/admin/governance")
@Tag(name = "管理端治理", description = "手动治理动作与同步状态")
public class AdminGovernanceController {

    private final AdminGovernanceService adminGovernanceService;

    public AdminGovernanceController(AdminGovernanceService adminGovernanceService) {
        this.adminGovernanceService = adminGovernanceService;
    }

    @Operation(summary = "手动触发推荐重算")
    @PostMapping("/recommendations/rebuild")
    public ApiResponse<Map<String, Object>> rebuildRecommendations() {
        return ApiResponse.ok(adminGovernanceService.rebuildRecommendations());
    }

    @Operation(summary = "手动刷新标签缓存")
    @PostMapping("/tags/refresh")
    public ApiResponse<Map<String, Object>> refreshTagsCache() {
        return ApiResponse.ok(adminGovernanceService.refreshTagsCache());
    }

    @Operation(summary = "查询同步状态（红黄绿）")
    @GetMapping("/sync-status")
    public ApiResponse<Map<String, Object>> syncStatus() {
        return ApiResponse.ok(adminGovernanceService.syncStatus());
    }
}
