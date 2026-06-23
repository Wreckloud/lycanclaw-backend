package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.content.service.ContentCatalogService;
import com.lycanclaw.backend.stats.config.ArticleMetricProperties;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.repository.ArticleMetricRepository;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章指标同步任务测试。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
class ArticleMetricSyncServiceTest {

    private final List<ThreadPoolTaskExecutor> executors = new ArrayList<>();

    @AfterEach
    void shutDownExecutors() {
        executors.forEach(ThreadPoolTaskExecutor::shutdown);
    }

    @Test
    void syncsThoughtAndKnowledgeAndDeletesRemovedSnapshots() throws Exception {
        Fixture fixture = fixture(List.of(
                article("/thoughts/one.html", ContentCatalogService.ContentKind.THOUGHT),
                article("/knowledge/java.html", ContentCatalogService.ContentKind.KNOWLEDGE)
        ));
        when(fixture.waline().fetchPageview("/thoughts/one.html")).thenReturn(10);
        when(fixture.waline().fetchCommentCount("/thoughts/one.html")).thenReturn(2);
        when(fixture.waline().fetchPageview("/knowledge/java.html")).thenReturn(20);
        when(fixture.waline().fetchCommentCount("/knowledge/java.html")).thenReturn(4);
        ArticleMetricEntity removed = metric("/thoughts/removed.html", 5, 1);
        when(fixture.repository().findAll()).thenReturn(List.of(removed));

        fixture.service().triggerAsyncSync("test");
        Map<String, Object> status = awaitDone(fixture.service());

        verify(fixture.repository()).saveAll(argThat(items -> Set.copyOf(paths(items))
                .equals(Set.of("/thoughts/one.html", "/knowledge/java.html"))));
        verify(fixture.repository()).deleteAll(argThat(items -> paths(items)
                .equals(List.of("/thoughts/removed.html"))));
        assertThat(status.get("successCount")).isEqualTo(2);
        assertThat(status.get("failureCount")).isEqualTo(0);
    }

    @Test
    void preservesFailedArticleAndWritesOnlySuccessfulArticles() throws Exception {
        Fixture fixture = fixture(List.of(
                article("/thoughts/success.html", ContentCatalogService.ContentKind.THOUGHT),
                article("/knowledge/failed.html", ContentCatalogService.ContentKind.KNOWLEDGE)
        ));
        when(fixture.waline().fetchPageview("/thoughts/success.html")).thenReturn(8);
        when(fixture.waline().fetchCommentCount("/thoughts/success.html")).thenReturn(1);
        when(fixture.waline().fetchPageview("/knowledge/failed.html"))
                .thenThrow(new IllegalStateException("Waline unavailable"));
        when(fixture.repository().findAll()).thenReturn(List.of(metric("/knowledge/failed.html", 99, 7)));

        fixture.service().triggerAsyncSync("test");
        Map<String, Object> status = awaitDone(fixture.service());

        verify(fixture.repository()).saveAll(argThat(items -> paths(items)
                .equals(List.of("/thoughts/success.html"))));
        verify(fixture.repository(), never()).deleteAll(org.mockito.ArgumentMatchers.any());
        assertThat(status.get("successCount")).isEqualTo(1);
        assertThat(status.get("failureCount")).isEqualTo(1);
    }

    @Test
    void writesNothingWhenEveryWalineRequestFails() throws Exception {
        Fixture fixture = fixture(List.of(article(
                "/thoughts/failed.html",
                ContentCatalogService.ContentKind.THOUGHT
        )));
        when(fixture.waline().fetchPageview("/thoughts/failed.html"))
                .thenThrow(new IllegalStateException("Waline unavailable"));

        fixture.service().triggerAsyncSync("test");
        Map<String, Object> status = awaitDone(fixture.service());

        verify(fixture.repository(), never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(fixture.repository(), never()).deleteAll(org.mockito.ArgumentMatchers.any());
        verify(fixture.repository(), never()).findAll();
        assertThat(status.get("successCount")).isEqualTo(0);
        assertThat(status.get("failureCount")).isEqualTo(1);
    }

    @Test
    void resetsRunningStateWhenExecutorRejectsSubmission() {
        ContentCatalogService catalog = mock(ContentCatalogService.class);
        ArticleMetricRepository repository = mock(ArticleMetricRepository.class);
        WalineGatewayClient waline = mock(WalineGatewayClient.class);
        AppTimeProvider timeProvider = mock(AppTimeProvider.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        when(timeProvider.nowOffsetString()).thenReturn("2026-06-23T10:00:00+08:00");
        org.mockito.Mockito.doThrow(new TaskRejectedException("executor stopped"))
                .when(executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        ArticleMetricSyncService service = new ArticleMetricSyncService(
                catalog,
                repository,
                waline,
                new ArticleMetricProperties(),
                executor,
                timeProvider
        );

        assertThatThrownBy(() -> service.triggerAsyncSync("test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("文章指标同步任务提交失败");
        assertThat(service.snapshotState().get("running")).isEqualTo(false);
        assertThat(service.snapshotState().get("lastError")).isEqualTo("executor stopped");
    }

    private Fixture fixture(List<ContentCatalogService.ContentItem> articles) {
        ContentCatalogService catalog = mock(ContentCatalogService.class);
        ArticleMetricRepository repository = mock(ArticleMetricRepository.class);
        WalineGatewayClient waline = mock(WalineGatewayClient.class);
        AppTimeProvider timeProvider = mock(AppTimeProvider.class);
        when(catalog.loadPublishedArticles()).thenReturn(articles);
        when(timeProvider.nowOffsetString()).thenReturn(
                "2026-06-23T10:00:00+08:00",
                "2026-06-23T10:00:01+08:00"
        );
        ArticleMetricProperties properties = new ArticleMetricProperties();
        properties.setFetchTimeoutSeconds(2);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.initialize();
        executors.add(executor);
        ArticleMetricSyncService service = new ArticleMetricSyncService(
                catalog,
                repository,
                waline,
                properties,
                executor,
                timeProvider
        );
        return new Fixture(service, repository, waline);
    }

    private Map<String, Object> awaitDone(ArticleMetricSyncService service) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            Map<String, Object> status = service.snapshotState();
            if (!Boolean.TRUE.equals(status.get("running"))) {
                return status;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("article metric sync did not finish");
    }

    private ContentCatalogService.ContentItem article(
            String path,
            ContentCatalogService.ContentKind kind
    ) {
        return new ContentCatalogService.ContentItem(path, path, "", "2026-06-23", List.of(), kind);
    }

    private ArticleMetricEntity metric(String path, int pageviews, int comments) {
        ArticleMetricEntity entity = new ArticleMetricEntity();
        entity.setPath(path);
        entity.setPageviewCount(pageviews);
        entity.setCommentCount(comments);
        return entity;
    }

    private List<String> paths(Iterable<? extends ArticleMetricEntity> entities) {
        List<String> paths = new ArrayList<>();
        entities.forEach(entity -> paths.add(entity.getPath()));
        return paths;
    }

    private record Fixture(
            ArticleMetricSyncService service,
            ArticleMetricRepository repository,
            WalineGatewayClient waline
    ) {
    }
}
