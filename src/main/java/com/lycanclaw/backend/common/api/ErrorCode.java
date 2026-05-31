package com.lycanclaw.backend.common.api;

/**
 * 统一错误码定义。
 * 用于描述接口错误类型及默认提示文案，便于拦截器和异常处理复用。
 * @author Wreckloud
 * @since 2026-05-15
 */
public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "参数错误"),
    NOT_FOUND("NOT_FOUND", "资源不存在"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器错误"),

    ADMIN_RATE_LIMITED("ADMIN_RATE_LIMITED", "请求频率过高，请稍后再试"),
    ADMIN_TOKEN_INVALID("ADMIN_TOKEN_INVALID", "管理员凭证无效或已过期"),

    MUSIC_RATE_LIMITED("MUSIC_RATE_LIMITED", "音乐接口请求过于频繁，请稍后再试");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
