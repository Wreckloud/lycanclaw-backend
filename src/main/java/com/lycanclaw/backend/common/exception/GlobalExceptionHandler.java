package com.lycanclaw.backend.common.exception;

import com.lycanclaw.backend.common.api.ErrorCode;
import com.lycanclaw.backend.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器。
 * 用于把业务异常和系统异常转换为统一响应结构。
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 参数校验类异常统一返回 400。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * 请求体、请求参数和参数类型错误统一返回稳定的400响应。
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class,
            BindException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleRequestFormat(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, "请求参数格式错误"));
    }

    /**
     * 未匹配路由统一返回 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ErrorCode.NOT_FOUND));
    }

    /**
     * 外部服务故障统一返回 502，并保留可理解的上游错误摘要。
     */
    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleUpstream(UpstreamServiceException ex) {
        log.warn("Upstream service request failed: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ErrorCode.UPSTREAM_ERROR, ex.getMessage()));
    }

    /**
     * 未被显式处理的异常统一返回 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled request error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }
}
