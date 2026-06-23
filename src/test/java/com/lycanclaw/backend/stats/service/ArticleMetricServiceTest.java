package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.stats.dto.ArticleMetricDto;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.repository.ArticleMetricRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文章指标查询服务测试。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
class ArticleMetricServiceTest {

    @Test
    void returnsStoredMetricForNormalizedPath() {
        ArticleMetricRepository repository = mock(ArticleMetricRepository.class);
        ArticleMetricService service = new ArticleMetricService(repository);
        ArticleMetricEntity existing = metric("/thoughts/known.html", 42, 3);
        when(repository.findById("/thoughts/known.html")).thenReturn(Optional.of(existing));

        ArticleMetricDto result = service.find("thoughts/known.html?from=test");

        assertThat(result).isEqualTo(new ArticleMetricDto("/thoughts/known.html", 42, 3));
    }

    @Test
    void returnsZeroWhenSnapshotDoesNotExist() {
        ArticleMetricRepository repository = mock(ArticleMetricRepository.class);
        ArticleMetricService service = new ArticleMetricService(repository);
        when(repository.findById("/knowledge/missing.html")).thenReturn(Optional.empty());

        ArticleMetricDto result = service.find("/knowledge/missing.html");

        assertThat(result).isEqualTo(new ArticleMetricDto("/knowledge/missing.html", 0, 0));
    }

    private ArticleMetricEntity metric(String path, int pageviews, int comments) {
        ArticleMetricEntity entity = new ArticleMetricEntity();
        entity.setPath(path);
        entity.setPageviewCount(pageviews);
        entity.setCommentCount(comments);
        return entity;
    }
}
