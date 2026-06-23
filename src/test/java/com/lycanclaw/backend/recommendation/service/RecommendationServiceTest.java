package com.lycanclaw.backend.recommendation.service;

import com.lycanclaw.backend.content.service.ContentCatalogService;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationPostDto;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.service.ArticleMetricService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 推荐排序业务测试。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
class RecommendationServiceTest {

    @Test
    void putsManualArticlesFirstAndExcludesBlockedAutomaticArticles() {
        ContentCatalogService catalogService = mock(ContentCatalogService.class);
        ArticleMetricService metricService = mock(ArticleMetricService.class);
        RecommendationRuleService ruleService = mock(RecommendationRuleService.class);
        RecommendationProperties properties = new RecommendationProperties();
        RecommendationService service = new RecommendationService(
                catalogService,
                metricService,
                ruleService,
                properties
        );

        List<ContentCatalogService.ContentItem> articles = List.of(
                article("/thoughts/automatic.html", "自动推荐", "2026-06-20 10:00:00"),
                article("/thoughts/older.html", "较早推荐", "2026-06-19 10:00:00"),
                article("/thoughts/manual.html", "手动推荐", "2026-06-18 10:00:00"),
                article("/thoughts/blocked.html", "排除文章", "2026-06-21 10:00:00")
        );
        when(catalogService.loadPublishedThoughts()).thenReturn(articles);
        when(metricService.loadEntities(List.of(
                "/thoughts/automatic.html",
                "/thoughts/older.html",
                "/thoughts/manual.html",
                "/thoughts/blocked.html"
        ))).thenReturn(Map.of(
                "/thoughts/automatic.html", metric("/thoughts/automatic.html", 100, 2),
                "/thoughts/older.html", metric("/thoughts/older.html", 100, 2),
                "/thoughts/manual.html", metric("/thoughts/manual.html", 1, 0),
                "/thoughts/blocked.html", metric("/thoughts/blocked.html", 1000, 20)
        ));
        when(ruleService.read()).thenReturn(new RecommendationManualConfigDto(
                List.of("/thoughts/manual.html"),
                List.of("/thoughts/blocked.html"),
                "2026-06-22T10:00:00+08:00"
        ));

        List<RecommendationPostDto> result = service.listRecommendations("", 4);

        assertThat(result).extracting(RecommendationPostDto::url)
                .containsExactly(
                        "/thoughts/manual.html",
                        "/thoughts/automatic.html",
                        "/thoughts/older.html"
                );
        assertThat(result.get(0).manualPinned()).isTrue();
        assertThat(result.get(1).manualPinned()).isFalse();
        assertThat(result.get(1).hotScore()).isEqualTo(148.0);
    }

    private ContentCatalogService.ContentItem article(String path, String title, String date) {
        return new ContentCatalogService.ContentItem(
                path,
                title,
                "摘要",
                date,
                List.of("测试"),
                ContentCatalogService.ContentKind.THOUGHT
        );
    }

    private ArticleMetricEntity metric(String path, int pageviews, int comments) {
        ArticleMetricEntity entity = new ArticleMetricEntity();
        entity.setPath(path);
        entity.setPageviewCount(pageviews);
        entity.setCommentCount(comments);
        return entity;
    }
}
