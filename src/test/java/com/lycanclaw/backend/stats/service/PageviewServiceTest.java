package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.content.service.ContentCatalogService;
import com.lycanclaw.backend.stats.dto.ArticleMetricDto;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 浏览量上报服务测试。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
class PageviewServiceTest {

    private static final String PATH = "/thoughts/test.html";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-23T10:00:00+08:00");

    @Test
    void rejectsUnknownArticlePath() {
        Fixture fixture = fixture();
        when(fixture.catalog().requirePublishedArticle("/fake.html"))
                .thenThrow(new IllegalArgumentException("文章不存在或未发布"));

        assertThatThrownBy(() -> fixture.service().updatePageview("/fake.html", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(fixture.waline(), never()).increasePageview(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deduplicatesSameIpAndPathForThirtyMinutes() {
        Fixture fixture = fixture();
        when(fixture.waline().increasePageview(PATH)).thenReturn(11);
        when(fixture.metricService().find(PATH)).thenReturn(new ArticleMetricDto(PATH, 11, 0));

        int first = fixture.service().updatePageview(PATH, "127.0.0.1");
        int duplicate = fixture.service().updatePageview(PATH, "127.0.0.1");

        assertThat(first).isEqualTo(11);
        assertThat(duplicate).isEqualTo(11);
        verify(fixture.waline()).increasePageview(PATH);
        verify(fixture.metricService()).updatePageview(PATH, 11);
    }

    @Test
    void allowsRetryAfterWalineFailure() {
        Fixture fixture = fixture();
        when(fixture.waline().increasePageview(PATH))
                .thenThrow(new IllegalStateException("Waline unavailable"))
                .thenReturn(12);

        assertThatThrownBy(() -> fixture.service().updatePageview(PATH, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.service().updatePageview(PATH, "127.0.0.1")).isEqualTo(12);

        verify(fixture.waline(), org.mockito.Mockito.times(2)).increasePageview(PATH);
    }

    @Test
    void returnsWalineCountWhenSnapshotSaveFails() {
        Fixture fixture = fixture();
        when(fixture.waline().increasePageview(PATH)).thenReturn(13);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(fixture.metricService()).updatePageview(PATH, 13);

        int result = fixture.service().updatePageview(PATH, "127.0.0.1");

        assertThat(result).isEqualTo(13);
    }

    private Fixture fixture() {
        WalineGatewayClient waline = mock(WalineGatewayClient.class);
        ArticleMetricService metricService = mock(ArticleMetricService.class);
        ContentCatalogService catalog = mock(ContentCatalogService.class);
        AppTimeProvider timeProvider = mock(AppTimeProvider.class);
        when(catalog.requirePublishedArticle(PATH)).thenReturn(new ContentCatalogService.ContentItem(
                PATH,
                "测试文章",
                "",
                "2026-06-23",
                List.of(),
                ContentCatalogService.ContentKind.THOUGHT
        ));
        when(timeProvider.nowOffsetDateTime()).thenReturn(NOW);
        return new Fixture(
                new PageviewService(waline, metricService, catalog, timeProvider),
                waline,
                metricService,
                catalog
        );
    }

    private record Fixture(
            PageviewService service,
            WalineGatewayClient waline,
            ArticleMetricService metricService,
            ContentCatalogService catalog
    ) {
    }
}
