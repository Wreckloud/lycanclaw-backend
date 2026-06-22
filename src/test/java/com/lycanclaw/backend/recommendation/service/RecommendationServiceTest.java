package com.lycanclaw.backend.recommendation.service;

import com.lycanclaw.backend.content.service.ArticleCatalogService;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationPostDto;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.service.ArticleMetricService;
import com.lycanclaw.backend.stats.service.ArticleMetricSyncService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    @Test
    void putsManualArticlesFirstAndExcludesBlockedAutomaticArticles() {
        ArticleCatalogService catalogService = mock(ArticleCatalogService.class);
        ArticleMetricService metricService = mock(ArticleMetricService.class);
        ArticleMetricSyncService syncService = mock(ArticleMetricSyncService.class);
        RecommendationRuleService ruleService = mock(RecommendationRuleService.class);
        RecommendationProperties properties = new RecommendationProperties();
        RecommendationService service = new RecommendationService(
                catalogService,
                metricService,
                syncService,
                ruleService,
                properties
        );

        List<ArticleCatalogService.ArticleCatalogItem> articles = List.of(
                article("/thoughts/automatic.html", "自动推荐", "2026-06-20T10:00:00"),
                article("/thoughts/manual.html", "手动推荐", "2026-06-18T10:00:00"),
                article("/thoughts/blocked.html", "排除文章", "2026-06-21T10:00:00")
        );
        when(catalogService.loadPublishedThoughts()).thenReturn(articles);
        when(metricService.loadEntities(List.of(
                "/thoughts/automatic.html",
                "/thoughts/manual.html",
                "/thoughts/blocked.html"
        ))).thenReturn(Map.of(
                "/thoughts/automatic.html", metric("/thoughts/automatic.html", 100, 2),
                "/thoughts/manual.html", metric("/thoughts/manual.html", 1, 0),
                "/thoughts/blocked.html", metric("/thoughts/blocked.html", 1000, 20)
        ));
        when(ruleService.read()).thenReturn(new RecommendationManualConfigDto(
                List.of("/thoughts/manual.html"),
                List.of("/thoughts/blocked.html"),
                "2026-06-22T10:00:00+08:00"
        ));

        List<RecommendationPostDto> result = service.listRecommendations("", 3);

        assertThat(result).extracting(RecommendationPostDto::url)
                .containsExactly("/thoughts/manual.html", "/thoughts/automatic.html");
        assertThat(result.get(0).manualPinned()).isTrue();
        assertThat(result.get(1).manualPinned()).isFalse();
    }

    private ArticleCatalogService.ArticleCatalogItem article(String path, String title, String date) {
        return new ArticleCatalogService.ArticleCatalogItem(path, title, "摘要", date, List.of("测试"));
    }

    private ArticleMetricEntity metric(String path, int pageviews, int comments) {
        ArticleMetricEntity entity = new ArticleMetricEntity();
        entity.setPath(path);
        entity.setPageviewCount(pageviews);
        entity.setCommentCount(comments);
        return entity;
    }
}
