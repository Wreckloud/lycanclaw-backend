package com.lycanclaw.backend.music.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * MusicQualityLevel：
 * 定义系统固定枚举值。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
public enum MusicQualityLevel {
    EXHIGH("exhigh"),
    HIGHER("higher"),
    STANDARD("standard"),
    JYMASTER("jymaster"),
    LOSSLESS("lossless"),
    HIRES("hires");

    private static final List<MusicQualityLevel> DEFAULT_PUBLIC_FALLBACK_CHAIN = List.of(
            EXHIGH,
            HIGHER,
            STANDARD
    );

    private final String value;

    MusicQualityLevel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<MusicQualityLevel> fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (MusicQualityLevel level : values()) {
            if (level.value.equals(normalized)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    public static MusicQualityLevel parseOrDefault(String raw, MusicQualityLevel fallback) {
        return fromValue(raw).orElse(fallback);
    }

    /**
     * 构建公开链路默认尝试顺序：优先指定值，再补齐 exhigh/higher/standard。
     */
    public static List<String> buildPublicAttemptOrder(String preferredRaw) {
        MusicQualityLevel preferred = parseOrDefault(preferredRaw, EXHIGH);
        List<String> order = new ArrayList<>();
        order.add(preferred.value());
        for (MusicQualityLevel fallback : DEFAULT_PUBLIC_FALLBACK_CHAIN) {
            if (!order.contains(fallback.value())) {
                order.add(fallback.value());
            }
        }
        return order;
    }
}
