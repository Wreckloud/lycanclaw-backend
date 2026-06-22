package com.lycanclaw.backend.admin.service;

import com.lycanclaw.backend.admin.dto.AdminOperationalConfigDto;
import com.lycanclaw.backend.music.config.MusicProperties;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.stats.config.ArticleMetricProperties;
import org.springframework.stereotype.Service;

@Service
public class AdminOperationalConfigService {

    private final MusicProperties musicProperties;
    private final RecommendationProperties recommendationProperties;
    private final ArticleMetricProperties articleMetricProperties;

    public AdminOperationalConfigService(
            MusicProperties musicProperties,
            RecommendationProperties recommendationProperties,
            ArticleMetricProperties articleMetricProperties
    ) {
        this.musicProperties = musicProperties;
        this.recommendationProperties = recommendationProperties;
        this.articleMetricProperties = articleMetricProperties;
    }

    public AdminOperationalConfigDto view() {
        return new AdminOperationalConfigDto(
                new AdminOperationalConfigDto.Music(
                        musicProperties.getPlaylistOwnerUid(),
                        musicProperties.getPreferredLevel()
                ),
                new AdminOperationalConfigDto.Recommendation(
                        recommendationProperties.getMaxCandidatePosts(),
                        recommendationProperties.getScore().getPageviewWeight(),
                        recommendationProperties.getScore().getCommentWeight()
                ),
                new AdminOperationalConfigDto.ArticleMetrics(
                        articleMetricProperties.getSyncIntervalMillis(),
                        articleMetricProperties.getFetchParallelism(),
                        articleMetricProperties.getFetchTimeoutSeconds()
                )
        );
    }
}
