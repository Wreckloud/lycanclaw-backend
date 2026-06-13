package com.lycanclaw.backend.common.exception;

/**
 * 外部服务调用异常。
 * 用于区分 Waline、网易云等上游故障与后端自身错误。
 * @author Wreckloud
 * @since 2026-06-13
 */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message) {
        super(message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
