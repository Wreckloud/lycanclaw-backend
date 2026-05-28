package com.lycanclaw.backend.common.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 系统健康状态等级（统一管理端 red/yellow/green 语义）。
 *
 * @author Wreckloud
 * @since 2026-05-28
 */
public enum HealthLevel {
    GREEN("green"),
    YELLOW("yellow"),
    RED("red");

    private final String value;

    HealthLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static HealthLevel fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return YELLOW;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (HealthLevel level : values()) {
            if (level.value.equals(normalized)) {
                return level;
            }
        }
        return YELLOW;
    }
}
