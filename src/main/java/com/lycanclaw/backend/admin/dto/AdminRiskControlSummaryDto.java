package com.lycanclaw.backend.admin.dto;

import java.util.List;

/**
 * 管理端风控摘要
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
public record AdminRiskControlSummaryDto(
        int adminApiRateLimitPerMinute,
        int publicMusicRateLimitPerMinute,
        String ipWhitelist,
        List<String> notes
) {
}
