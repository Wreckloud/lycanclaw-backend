package com.lycanclaw.backend.music.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 二维码登录状态。
 * 对应网易云扫码登录接口返回的状态码。
 * @author Wreckloud
 * @since 2026-05-15
 */
public enum MusicQrLoginStatus {
    EXPIRED(800, "expired", "二维码已过期"),
    WAIT_SCAN(801, "wait_scan", "等待扫码"),
    WAIT_CONFIRM(802, "wait_confirm", "已扫码，等待确认"),
    LOGIN_SUCCESS(803, "login_success", "登录成功"),
    UNKNOWN(-1, "unknown", "未知状态");

    private final int code;
    private final String value;
    private final String message;

    MusicQrLoginStatus(int code, String value, String message) {
        this.code = code;
        this.value = value;
        this.message = message;
    }

    public int code() {
        return code;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public String message() {
        return message;
    }

    public boolean loggedIn() {
        return this == LOGIN_SUCCESS;
    }

    public static MusicQrLoginStatus fromCode(int code) {
        for (MusicQrLoginStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
