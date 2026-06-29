package com.lycanclaw.backend.admin.dto;

/**
 * 管理端只读运维配置。
 * 展示音乐、推荐和文章指标的当前服务器配置。
 *
 * @author Wreckloud
 * @since 2026-06-03
 */
public record AdminOperationalConfigDto(
        Music music,
        Recommendation recommendation,
        ArticleMetrics articleMetrics
) {

    public record Music(String rankingOwnerUid, String preferredLevel) {
    }

    public record Recommendation(int maxCandidatePosts, double pageviewWeight, double commentWeight) {
    }

    public record ArticleMetrics(long syncIntervalMillis, int fetchParallelism, int fetchTimeoutSeconds) {
    }
}
