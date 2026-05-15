package com.lycanclaw.backend.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @Description 统一错误体模型
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Schema(description = "错误信息")
public record ApiError(
        @Schema(description = "错误码", example = "参数错误")
        String code,
        @Schema(description = "错误说明", example = "id 参数不能为空")
        String message
) {
}
