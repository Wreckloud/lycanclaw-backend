package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.stats.dto.ArticleMetricDto;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.repository.ArticleMetricRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleMetricServiceTest {

    @Test
    void returnsMetricsInRequestedOrderAndFillsMissingArticlesWithZero() {
        ArticleMetricRepository repository = mock(ArticleMetricRepository.class);
        ArticleMetricService service = new ArticleMetricService(repository, new AppTimeProvider("Asia/Shanghai"));
        ArticleMetricEntity existing = new ArticleMetricEntity();
        existing.setPath("/thoughts/known.html");
        existing.setPageviewCount(42);
        existing.setCommentCount(3);
        existing.setSyncedAt(OffsetDateTime.parse("2026-06-22T10:00:00+08:00"));
        when(repository.findAllByPathIn(List.of("/thoughts/known.html", "/thoughts/missing.html")))
                .thenReturn(List.of(existing));

        List<ArticleMetricDto> result = service.findBatch(List.of(
                "thoughts/known.html",
                "/thoughts/missing.html",
                "/thoughts/known.html"
        ));

        assertThat(result).extracting(ArticleMetricDto::path)
                .containsExactly("/thoughts/known.html", "/thoughts/missing.html");
        assertThat(result.get(0).pageviewCount()).isEqualTo(42);
        assertThat(result.get(0).commentCount()).isEqualTo(3);
        assertThat(result.get(1).pageviewCount()).isZero();
        assertThat(result.get(1).commentCount()).isZero();
        assertThat(result.get(1).syncedAt()).isNull();
    }
}
