package com.lycanclaw.backend.common.exception;

import com.lycanclaw.backend.common.api.ErrorCodes;
import com.lycanclaw.backend.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @Description 全局异常处理器
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数校验类异常统一返回 400。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCodes.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * 未被显式处理的异常统一返回 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCodes.INTERNAL_ERROR, ex.getMessage()));
    }
}
