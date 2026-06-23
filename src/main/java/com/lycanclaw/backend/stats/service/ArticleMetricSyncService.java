package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.content.service.ContentCatalogService;
import com.lycanclaw.backend.stats.config.ArticleMetricProperties;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.repository.ArticleMetricRepository;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 定期从 Waline 同步文章浏览量和评论数快照。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
@Service
public class ArticleMetricSyncService {

    private static final Logger log = LoggerFactory.getLogger(ArticleMetricSyncService.class);

    private final ContentCatalogService contentCatalogService;
    private final ArticleMetricRepository repository;
    private final WalineGatewayClient walineGatewayClient;
    private final ArticleMetricProperties properties;
    private final ThreadPoolTaskExecutor executor;
    private final AppTimeProvider appTimeProvider;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<SyncStatus> latestStatus = new AtomicReference<>(SyncStatus.initial());

    public ArticleMetricSyncService(
            ContentCatalogService contentCatalogService,
            ArticleMetricRepository repository,
            WalineGatewayClient walineGatewayClient,
            ArticleMetricProperties properties,
            @Qualifier("articleMetricExecutor") ThreadPoolTaskExecutor executor,
            AppTimeProvider appTimeProvider
    ) {
        this.contentCatalogService = contentCatalogService;
        this.repository = repository;
        this.walineGatewayClient = walineGatewayClient;
        this.properties = properties;
        this.executor = executor;
        this.appTimeProvider = appTimeProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (properties.isStartupSyncEnabled()) {
            triggerAsyncSync("startup");
        }
    }

    @Scheduled(
            fixedDelayString = "${lycan.article-metrics.sync-interval-millis:600000}",
            initialDelayString = "${lycan.article-metrics.sync-interval-millis:600000}"
    )
    public void runScheduledSync() {
        triggerAsyncSync("scheduled");
    }

    public Map<String, Object> triggerAsyncSync(String trigger) {
        // 启动、定时和手动触发共用此入口，同一时间只允许一个同步批次运行。
        if (!running.compareAndSet(false, true)) {
            return response(false);
        }
        try {
            executor.execute(() -> executeSync(trigger));
        } catch (RuntimeException ex) {
            String failedAt = appTimeProvider.nowOffsetString();
            latestStatus.set(new SyncStatus(failedAt, failedAt, 0, 0, errorMessage(ex)));
            running.set(false);
            throw new IllegalStateException("文章指标同步任务提交失败", ex);
        }
        return response(true);
    }

    public Map<String, Object> snapshotState() {
        return response(null);
    }

    private Map<String, Object> response(Boolean accepted) {
        SyncStatus status = latestStatus.get();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (accepted != null) {
            payload.put("accepted", accepted);
        }
        payload.put("running", running.get());
        payload.put("lastStartedAt", status.startedAt());
        payload.put("lastFinishedAt", status.finishedAt());
        payload.put("successCount", status.successCount());
        payload.put("failureCount", status.failureCount());
        payload.put("lastError", status.lastError());
        return payload;
    }

    private void executeSync(String trigger) {
        String startedAt = appTimeProvider.nowOffsetString();
        latestStatus.set(new SyncStatus(startedAt, "", 0, 0, ""));
        try {
            List<ContentCatalogService.ContentItem> articles = contentCatalogService.loadPublishedArticles();
            SyncBatch batch = fetchBatch(articles);
            persistBatch(articles, batch);
            latestStatus.set(new SyncStatus(
                    startedAt,
                    appTimeProvider.nowOffsetString(),
                    batch.successCount(),
                    batch.failureCount(),
                    batch.lastError()
            ));
            if (batch.failureCount() > 0) {
                log.warn(
                        "article metric sync partially failed, trigger={}, articles={}, success={}, failed={}, error={}",
                        trigger,
                        articles.size(),
                        batch.successCount(),
                        batch.failureCount(),
                        batch.lastError()
                );
            }
        } catch (Exception ex) {
            latestStatus.set(new SyncStatus(
                    startedAt,
                    appTimeProvider.nowOffsetString(),
                    0,
                    0,
                    errorMessage(ex)
            ));
            log.error("article metric sync failed, trigger={}", trigger, ex);
        } finally {
            running.set(false);
        }
    }

    private SyncBatch fetchBatch(List<ContentCatalogService.ContentItem> articles) {
        List<Future<MetricFetchResult>> futures = articles.stream()
                .map(article -> executor.submit(() -> fetchOne(article.path())))
                .toList();
        List<ArticleMetricEntity> successfulEntities = new ArrayList<>(articles.size());
        int failureCount = 0;
        String lastError = "";
        int timeoutSeconds = Math.max(2, properties.getFetchTimeoutSeconds());

        for (int index = 0; index < articles.size(); index++) {
            String path = articles.get(index).path();
            MetricFetchResult result;
            try {
                result = futures.get(index).get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                futures.get(index).cancel(true);
                result = MetricFetchResult.failed(path, "fetch timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                result = MetricFetchResult.failed(path, "fetch interrupted");
            } catch (ExecutionException ex) {
                result = MetricFetchResult.failed(path, errorMessage(ex));
            }

            // 仅写入完整成功项；失败项不落库，因此会保留原有快照。
            if (result.success()) {
                successfulEntities.add(toEntity(result));
            } else {
                failureCount++;
                lastError = result.errorMessage();
            }
        }
        return new SyncBatch(successfulEntities, successfulEntities.size(), failureCount, lastError);
    }

    private MetricFetchResult fetchOne(String path) {
        try {
            int pageviewCount = Math.max(0, walineGatewayClient.fetchPageview(path));
            int commentCount = Math.max(0, walineGatewayClient.fetchCommentCount(path));
            return MetricFetchResult.success(path, pageviewCount, commentCount);
        } catch (Exception ex) {
            return MetricFetchResult.failed(path, errorMessage(ex));
        }
    }

    private void persistBatch(
            List<ContentCatalogService.ContentItem> articles,
            SyncBatch batch
    ) {
        // 全部请求失败时不改动快照，避免一次上游故障破坏现有数据。
        if (!articles.isEmpty() && batch.successCount() == 0) {
            return;
        }
        if (!batch.entities().isEmpty()) {
            repository.saveAll(batch.entities());
        }
        Set<String> publishedPaths = articles.stream()
                .map(ContentCatalogService.ContentItem::path)
                .collect(HashSet::new, Set::add, Set::addAll);
        List<ArticleMetricEntity> removed = repository.findAll().stream()
                .filter(entity -> !publishedPaths.contains(entity.getPath()))
                .toList();
        if (!removed.isEmpty()) {
            repository.deleteAll(removed);
        }
    }

    private ArticleMetricEntity toEntity(MetricFetchResult result) {
        ArticleMetricEntity entity = new ArticleMetricEntity();
        entity.setPath(result.path());
        entity.setPageviewCount(result.pageviewCount());
        entity.setCommentCount(result.commentCount());
        return entity;
    }

    private String errorMessage(Exception ex) {
        Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private record SyncBatch(
            List<ArticleMetricEntity> entities,
            int successCount,
            int failureCount,
            String lastError
    ) {
    }

    private record MetricFetchResult(
            String path,
            int pageviewCount,
            int commentCount,
            String errorMessage,
            boolean success
    ) {
        private static MetricFetchResult success(String path, int pageviewCount, int commentCount) {
            return new MetricFetchResult(path, pageviewCount, commentCount, "", true);
        }

        private static MetricFetchResult failed(String path, String error) {
            return new MetricFetchResult(path, 0, 0, error, false);
        }
    }

    private record SyncStatus(
            String startedAt,
            String finishedAt,
            int successCount,
            int failureCount,
            String lastError
    ) {
        private static SyncStatus initial() {
            return new SyncStatus("", "", 0, 0, "");
        }
    }
}
