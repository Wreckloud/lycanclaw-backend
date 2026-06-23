package com.lycanclaw.backend.recommendation.service;

import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.content.service.ContentCatalogService;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.entity.RecommendationRuleEntity;
import com.lycanclaw.backend.recommendation.repository.RecommendationRuleRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 推荐规则服务测试。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
class RecommendationRuleServiceTest {

    @Test
    void hidesRulesForDeletedArticles() {
        RecommendationRuleRepository repository = mock(RecommendationRuleRepository.class);
        ContentCatalogService catalog = mock(ContentCatalogService.class);
        AppTimeProvider timeProvider = mock(AppTimeProvider.class);
        RecommendationRuleService service = new RecommendationRuleService(repository, catalog, timeProvider);
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-06-23T10:00:00+08:00");

        when(catalog.loadPublishedThoughts()).thenReturn(List.of(article("/thoughts/active.html")));
        when(repository.findAll()).thenReturn(List.of(
                rule("/thoughts/active.html", 1, false, updatedAt),
                rule("/thoughts/deleted.html", 2, false, updatedAt.plusMinutes(1))
        ));
        when(timeProvider.toOffsetString(updatedAt)).thenReturn(updatedAt.toString());

        RecommendationManualConfigDto result = service.read();

        assertThat(result.manualUrls()).containsExactly("/thoughts/active.html");
        assertThat(result.excludedUrls()).isEmpty();
        assertThat(result.updatedAt()).isEqualTo(updatedAt.toString());
    }

    private ContentCatalogService.ContentItem article(String path) {
        return new ContentCatalogService.ContentItem(
                path,
                path,
                "",
                "2026-06-23 10:00:00",
                List.of(),
                ContentCatalogService.ContentKind.THOUGHT
        );
    }

    private RecommendationRuleEntity rule(
            String path,
            Integer rank,
            boolean excluded,
            OffsetDateTime updatedAt
    ) {
        RecommendationRuleEntity entity = new RecommendationRuleEntity();
        entity.setPath(path);
        entity.setManualRank(rank);
        entity.setExcluded(excluded);
        entity.setUpdatedAt(updatedAt);
        return entity;
    }
}
