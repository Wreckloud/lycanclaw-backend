package com.lycanclaw.backend.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一接口错误对象。
 * 用于描述失败响应中的错误码和错误消息。
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
