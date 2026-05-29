package com.lycanclaw.backend.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ApiError：
 * 定义统一 API 协议与响应结构。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "错误信息")
public record ApiError(
        @Schema(description = "错误码", example = "BAD_REQUEST")
        String code,
        @Schema(description = "错误说明", example = "id 参数不能为空")
        String message
) {
}
