package com.lycanclaw.backend.admin.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.runtimeconfig.dto.RuntimeConfigDto;
import com.lycanclaw.backend.runtimeconfig.dto.RuntimeConfigResponseDto;
import com.lycanclaw.backend.runtimeconfig.service.RuntimeConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端运行时配置接口。
 * 提供非敏感配置的读取、保存与恢复默认能力。
 * @author Wreckloud
 * @since 2026-06-03
 */
@RestController
@RequestMapping("/api/admin/config/runtime")
@Tag(name = "管理端运行配置", description = "后台可编辑的非敏感运行时配置")
public class AdminRuntimeConfigController {

    private final RuntimeConfigService runtimeConfigService;

    public AdminRuntimeConfigController(RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    @Operation(summary = "读取运行时配置")
    @GetMapping
    public ApiResponse<RuntimeConfigResponseDto> getRuntimeConfig() {
        return ApiResponse.ok(runtimeConfigService.view());
    }

    @Operation(summary = "保存运行时配置")
    @PutMapping
    public ApiResponse<RuntimeConfigResponseDto> updateRuntimeConfig(@RequestBody RuntimeConfigDto request) {
        return ApiResponse.ok(runtimeConfigService.update(request));
    }

    @Operation(summary = "恢复默认运行时配置")
    @PostMapping("/reset")
    public ApiResponse<RuntimeConfigResponseDto> resetRuntimeConfig() {
        return ApiResponse.ok(runtimeConfigService.reset());
    }
}
