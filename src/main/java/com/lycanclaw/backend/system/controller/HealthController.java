package com.lycanclaw.backend.system.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * @Description 系统健康检查接口
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "系统", description = "系统运行状态检查")
public class HealthController {

    /**
     * 用于部署探活与联调确认的轻量健康检查接口。
     */
    @Operation(summary = "健康检查")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "service", "lycanclaw-backend",
                "status", "运行中",
                "timestamp", Instant.now().toString()
        ));
    }
}
