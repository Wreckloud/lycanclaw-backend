package com.lycanclaw.backend.stats.dto;

public record ArticleMetricDto(
        String path,
        int pageviewCount,
        int commentCount,
        String syncedAt
) {
}
