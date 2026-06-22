package com.lycanclaw.backend.admin.dto;

public record AdminOperationalConfigDto(
        Music music,
        Recommendation recommendation,
        ArticleMetrics articleMetrics
) {

    public record Music(String playlistOwnerUid, String preferredLevel) {
    }

    public record Recommendation(int maxCandidatePosts, double pageviewWeight, double commentWeight) {
    }

    public record ArticleMetrics(long syncIntervalMillis, int fetchParallelism, int fetchTimeoutSeconds) {
    }
}
