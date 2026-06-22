package com.lycanclaw.backend.common.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 系统时间提供器。
 * 用于统一输出带时区的当前时间，避免散落时间实现。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class AppTimeProvider {

    private final ZoneId zoneId;

    public AppTimeProvider(@Value("${lycan.system.zone-id:Asia/Shanghai}") String zoneId) {
        this.zoneId = ZoneId.of(zoneId);
    }

    public String nowOffsetString() {
        return nowOffsetDateTime().toString();
    }

    public OffsetDateTime nowOffsetDateTime() {
        return OffsetDateTime.now(zoneId);
    }

    public String toOffsetString(Instant instant) {
        return instant == null ? "" : instant.atZone(zoneId).toOffsetDateTime().toString();
    }

    public String toOffsetString(OffsetDateTime dateTime) {
        return dateTime == null ? "" : dateTime.toString();
    }
}
