package com.lycanclaw.backend.runtimeconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 运行时配置响应。
 * 用于向管理端返回当前生效配置、默认配置和只读环境摘要。
 * @author Wreckloud
 * @since 2026-06-03
 */
@Schema(description = "运行时配置响应")
public record RuntimeConfigResponseDto(
        @Schema(description = "当前生效的可编辑配置")
        RuntimeConfigDto editable,
        @Schema(description = "application.yml 提供的默认配置")
        RuntimeConfigDto defaults,
        @Schema(description = "只读配置摘要，敏感信息不返回原文")
        Map<String, Object> readonly,
        @Schema(description = "运行时配置文件是否存在")
        boolean stored,
        @Schema(description = "运行时配置最后更新时间")
        String updatedAt
) {
}
