package com.lycanclaw.backend.common.api;

/**
 * @Description 统一错误码常量
 * @Author Wreckloud
 * @Date 2026-05-15
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    public static final String ADMIN_RATE_LIMITED = "ADMIN_RATE_LIMITED";
    public static final String ADMIN_IP_FORBIDDEN = "ADMIN_IP_FORBIDDEN";
    public static final String ADMIN_TOKEN_INVALID = "ADMIN_TOKEN_INVALID";

    public static final String MUSIC_RATE_LIMITED = "MUSIC_RATE_LIMITED";
}
