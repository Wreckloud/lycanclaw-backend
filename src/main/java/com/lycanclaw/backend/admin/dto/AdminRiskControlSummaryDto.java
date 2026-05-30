package com.lycanclaw.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 管理风控控制摘要数据模型。
 * 用于在管理风控控制摘要业务链路中传递结构化数据。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "管理端风控摘要")
public record AdminRiskControlSummaryDto(
        @Schema(description = "管理端接口限流阈值（每分钟）", example = "30")
        int adminApiRateLimitPerMinute,
        @Schema(description = "公开音乐接口限流阈值（每分钟）", example = "120")
        int publicMusicRateLimitPerMinute,
        @Schema(description = "管理端鉴权模式说明", example = "静态 token + Waline 会话 token")
        String authMode,
        @Schema(description = "风控说明列表")
        List<String> notes
) {
}
