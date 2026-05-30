package com.lycanclaw.backend.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一接口响应结构。
 * 用于封装业务数据、错误码与提示信息，保持前后端响应格式一致。
 * @author Wreckloud
 * @since 2026-05-15
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

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, new ApiError(errorCode.code(), errorCode.defaultMessage()));
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        String finalMessage = message == null || message.isBlank() ? errorCode.defaultMessage() : message;
        return new ApiResponse<>(false, null, new ApiError(errorCode.code(), finalMessage));
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message));
    }
}
