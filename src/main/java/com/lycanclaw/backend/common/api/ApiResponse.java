package com.lycanclaw.backend.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @Description 统一响应体模型
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Schema(description = "统一响应结构")
public record ApiResponse<T>(
        @Schema(description = "是否成功", example = "true")
        boolean success,
        @Schema(description = "业务数据")
        T data,
        @Schema(description = "错误信息，成功时为 null")
        ApiError error
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message));
    }
}
