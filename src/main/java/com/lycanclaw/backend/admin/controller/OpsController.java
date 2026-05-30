package com.lycanclaw.backend.admin.controller;

import com.lycanclaw.backend.admin.service.OpsCheckService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运维接口控制器。
 * 用于提供运维相关 REST 接口。
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/admin/ops")
@Tag(name = "运维", description = "部署后的运维检查项")
public class OpsController {

    private final OpsCheckService opsCheckService;

    public OpsController(OpsCheckService opsCheckService) {
        this.opsCheckService = opsCheckService;
    }

    @Operation(summary = "查询运维检查项")
    @GetMapping("/checks")
    public ApiResponse<Map<String, Object>> checks() {
        return ApiResponse.ok(opsCheckService.collectChecks());
    }
}
