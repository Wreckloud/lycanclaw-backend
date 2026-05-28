package com.lycanclaw.backend.common.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 应用统一时间提供器
 *
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
        return OffsetDateTime.now(zoneId).toString();
    }
}
