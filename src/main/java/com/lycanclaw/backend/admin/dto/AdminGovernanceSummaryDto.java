package com.lycanclaw.backend.admin.dto;

import java.util.Map;

/**
 * 管理端治理摘要
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
public record AdminGovernanceSummaryDto(
        boolean ok,
        String message,
        int manualRecommendationCount,
        String manualRecommendationUpdatedAt,
        int thoughtTagCount,
        int thoughtPostCount,
        int recentCommentCount,
        String syncLevel,
        String syncCheckedAt,
        Map<String, String> actions
) {
}
