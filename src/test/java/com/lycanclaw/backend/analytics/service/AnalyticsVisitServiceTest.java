package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.VisitEndRequest;
import com.lycanclaw.backend.analytics.dto.VisitStartRequest;
import com.lycanclaw.backend.analytics.entity.AnalyticsVisitEntity;
import com.lycanclaw.backend.analytics.repository.AnalyticsVisitRepository;
import com.lycanclaw.backend.common.security.ClientIpResolver;
import com.lycanclaw.backend.content.service.ContentCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 页面访问统计服务测试。
 * 验证公开路径记录、非法路径拒绝和重复结算边界。
 *
 * @author Wreckloud
 * @since 2026-06-24
 */
class AnalyticsVisitServiceTest {

    @Test
    void createsVisitWithServerDerivedFields() {
        AnalyticsVisitRepository repository = mock(AnalyticsVisitRepository.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(clientIpResolver.resolve(servletRequest)).thenReturn("203.0.113.10");
        when(servletRequest.getHeader("User-Agent")).thenReturn("Test Browser");
        when(repository.save(any(AnalyticsVisitEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AnalyticsVisitService service = createService(repository, clientIpResolver);

        service.start(
                new VisitStartRequest(
                        "/thoughts/example.html?from=home",
                        "示例文章",
                        "https://wreckloud.com/",
                        "visitor-1"
                ),
                servletRequest
        );

        ArgumentCaptor<AnalyticsVisitEntity> captor = ArgumentCaptor.forClass(AnalyticsVisitEntity.class);
        verify(repository).save(captor.capture());
        AnalyticsVisitEntity saved = captor.getValue();
        assertThat(saved.getPath()).isEqualTo("/thoughts/example.html");
        assertThat(saved.getPageType()).isEqualTo("article");
        assertThat(saved.getVisitorId()).isEqualTo("visitor-1");
        assertThat(saved.getIp()).isEqualTo("203.0.113.10");
        assertThat(saved.getUserAgent()).isEqualTo("Test Browser");
    }

    @Test
    void rejectsNonPublicPathBeforeSaving() {
        AnalyticsVisitRepository repository = mock(AnalyticsVisitRepository.class);
        AnalyticsVisitService service = createService(repository, mock(ClientIpResolver.class));

        assertThatThrownBy(() -> service.start(
                new VisitStartRequest("/admin/", "管理端", "", "visitor-1"),
                mock(HttpServletRequest.class)
        )).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsUnknownPublishedPathBeforeSaving() {
        AnalyticsVisitRepository repository = mock(AnalyticsVisitRepository.class);
        AnalyticsVisitService service = createService(repository, mock(ClientIpResolver.class));

        assertThatThrownBy(() -> service.start(
                new VisitStartRequest("/thoughts/deleted.html", "已删除文章", "", "visitor-1"),
                mock(HttpServletRequest.class)
        )).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsNotFoundTitleBeforeSaving() {
        AnalyticsVisitRepository repository = mock(AnalyticsVisitRepository.class);
        AnalyticsVisitService service = createService(repository, mock(ClientIpResolver.class));

        assertThatThrownBy(() -> service.start(
                new VisitStartRequest("/", "404", "", "visitor-1"),
                mock(HttpServletRequest.class)
        )).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void keepsLargestSettlementWithinServerLimits() {
        AnalyticsVisitRepository repository = mock(AnalyticsVisitRepository.class);
        AnalyticsVisitEntity entity = new AnalyticsVisitEntity();
        entity.setVisitId("visit-1");
        entity.setDurationMs(5_000);
        entity.setMaxScrollPercent(40);
        when(repository.findByVisitId("visit-1")).thenReturn(Optional.of(entity));
        AnalyticsVisitService service = createService(repository, mock(ClientIpResolver.class));

        service.end(new VisitEndRequest("visit-1", 9_999_999L, 150));
        service.end(new VisitEndRequest("visit-1", 1_000L, 10));

        assertThat(entity.getDurationMs()).isEqualTo(30 * 60 * 1000L);
        assertThat(entity.getMaxScrollPercent()).isEqualTo(100);
        assertThat(entity.getEndedAt()).isNotNull();
    }

    private AnalyticsVisitService createService(
            AnalyticsVisitRepository repository,
            ClientIpResolver clientIpResolver
    ) {
        ContentCatalogService contentCatalogService = mock(ContentCatalogService.class);
        when(contentCatalogService.findPublishedArticle("/thoughts/example.html"))
                .thenReturn(Optional.of(new ContentCatalogService.ContentItem(
                        "/thoughts/example.html",
                        "示例文章",
                        "",
                        "2026-06-24 12:00:00",
                        List.of("测试"),
                        ContentCatalogService.ContentKind.THOUGHT
                )));
        return new AnalyticsVisitService(
                repository,
                new AnalyticsPathPolicy(),
                contentCatalogService,
                clientIpResolver,
                "Asia/Shanghai"
        );
    }
}
