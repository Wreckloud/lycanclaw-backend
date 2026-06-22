package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.content.service.ArticleCatalogService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;

@Service
public class ArticleMetricSyncService {

    private static final Logger log = LoggerFactory.getLogger(ArticleMetricSyncService.class);

    private final ArticleCatalogService articleCatalogService;
    private final ArticleMetricRepository repository;
    private final WalineGatewayClient walineGatewayClient;
    private final ArticleMetricProperties properties;
    private final ThreadPoolTaskExecutor executor;
    private final AppTimeProvider appTimeProvider;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<SyncStatus> latestStatus = new AtomicReference<>(SyncStatus.initial());

    public ArticleMetricSyncService(
            ArticleCatalogService articleCatalogService,
            ArticleMetricRepository repository,
            WalineGatewayClient walineGatewayClient,
            ArticleMetricProperties properties,
            @Qualifier("articleMetricExecutor") ThreadPoolTaskExecutor executor,
            AppTimeProvider appTimeProvider
    ) {
        this.articleCatalogService = articleCatalogService;
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
            fixedDelayString = "${lycan.article-metrics.sync-interval-millis:300000}",
            initialDelayString = "${lycan.article-metrics.sync-interval-millis:300000}"
    )
    public void runScheduledSync() {
        triggerAsyncSync("scheduled");
    }

    public Map<String, Object> triggerAsyncSync(String trigger) {
        if (!running.compareAndSet(false, true)) {
            Map<String, Object> response = new LinkedHashMap<>(snapshotState());
            response.put("accepted", false);
            response.put("trigger", trigger);
            return response;
        }
        executor.execute(() -> executeSync(trigger));
        Map<String, Object> response = new LinkedHashMap<>(snapshotState());
        response.put("accepted", true);
        response.put("trigger", trigger);
        return response;
    }

    public Map<String, Object> snapshotState() {
        SyncStatus status = latestStatus.get();
        long metricCount = repository.count();
        OffsetDateTime lastSuccessAt = repository.findTopByOrderBySyncedAtDesc()
                .map(ArticleMetricEntity::getSyncedAt)
                .orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("running", running.get());
        payload.put("metricCount", metricCount);
        payload.put("hasSnapshot", metricCount > 0);
        payload.put("expired", isExpired(lastSuccessAt));
        payload.put("lastStartedAt", status.startedAt());
        payload.put("lastFinishedAt", status.finishedAt());
        payload.put("lastTrigger", status.trigger());
        payload.put("successCount", status.successCount());
        payload.put("failureCount", status.failureCount());
        payload.put("lastError", status.lastError());
        payload.put("lastSuccessAt", lastSuccessAt == null ? "" : appTimeProvider.toOffsetString(lastSuccessAt));
        return payload;
    }

    private boolean isExpired(OffsetDateTime lastSuccessAt) {
        if (lastSuccessAt == null) {
            return true;
        }
        long staleAfterMillis = Math.max(60_000L, properties.getSyncIntervalMillis() * 3L);
        return lastSuccessAt.isBefore(appTimeProvider.nowOffsetDateTime().minusNanos(staleAfterMillis * 1_000_000L));
    }

    private void executeSync(String trigger) {
        String startedAt = appTimeProvider.nowOffsetString();
        latestStatus.set(new SyncStatus(trigger, startedAt, "", 0, 0, ""));
        try {
            List<ArticleCatalogService.ArticleCatalogItem> articles = articleCatalogService.loadPublishedThoughts();
            Map<String, ArticleMetricEntity> existing = repository.findAllByPathIn(
                    articles.stream().map(ArticleCatalogService.ArticleCatalogItem::path).toList()
            ).stream().collect(Collectors.toMap(ArticleMetricEntity::getPath, entity -> entity));
            SyncBatch batch = fetchBatch(articles, existing);
            if (!batch.entities().isEmpty()) {
                repository.saveAll(batch.entities());
            }
            latestStatus.set(new SyncStatus(
                    trigger,
                    startedAt,
                    appTimeProvider.nowOffsetString(),
                    batch.successCount(),
                    batch.failureCount(),
                    batch.lastError()
            ));
            log.info(
                    "article metric sync finished, trigger={}, articles={}, success={}, failed={}",
                    trigger,
                    articles.size(),
                    batch.successCount(),
                    batch.failureCount()
            );
        } catch (Exception ex) {
            latestStatus.set(new SyncStatus(
                    trigger,
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

    private SyncBatch fetchBatch(
            List<ArticleCatalogService.ArticleCatalogItem> articles,
            Map<String, ArticleMetricEntity> existing
    ) {
        List<Future<MetricFetchResult>> futures = articles.stream()
                .map(article -> executor.submit(() -> fetchOne(article.path())))
                .toList();
        List<ArticleMetricEntity> entities = new ArrayList<>(articles.size());
        int successCount = 0;
        int failureCount = 0;
        String lastError = "";
        int timeoutSeconds = Math.max(2, properties.getFetchTimeoutSeconds());

        for (int index = 0; index < articles.size(); index++) {
            String path = articles.get(index).path();
            MetricFetchResult result;
            try {
                result = futures.get(index).get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                result = MetricFetchResult.failed(path, "fetch timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                result = MetricFetchResult.failed(path, "fetch interrupted");
            } catch (ExecutionException ex) {
                result = MetricFetchResult.failed(path, errorMessage(ex));
            }

            ArticleMetricEntity entity = merge(result, existing.get(path));
            if (entity != null) {
                entities.add(entity);
            }
            if (result.success()) {
                successCount++;
            } else {
                failureCount++;
                lastError = result.errorMessage();
            }
        }
        return new SyncBatch(entities, successCount, failureCount, lastError);
    }

    private MetricFetchResult fetchOne(String path) {
        Integer pageviewCount = null;
        Integer commentCount = null;
        List<String> errors = new ArrayList<>();
        try {
            pageviewCount = Math.max(0, walineGatewayClient.fetchPageview(path));
        } catch (Exception ex) {
            errors.add("pageview: " + errorMessage(ex));
        }
        try {
            commentCount = Math.max(0, walineGatewayClient.fetchCommentCount(path));
        } catch (Exception ex) {
            errors.add("comment: " + errorMessage(ex));
        }

        if (errors.isEmpty()) {
            return new MetricFetchResult(path, pageviewCount, commentCount, "ok", "", true);
        }
        return new MetricFetchResult(
                path,
                pageviewCount,
                commentCount,
                pageviewCount == null && commentCount == null ? "failed" : "partial_error",
                String.join("; ", errors),
                false
        );
    }

    private ArticleMetricEntity merge(MetricFetchResult result, ArticleMetricEntity existing) {
        if (result.pageviewCount() == null && result.commentCount() == null) {
            if (existing != null) {
                existing.setSourceStatus(result.sourceStatus());
                existing.setLastError(result.errorMessage());
            }
            return existing;
        }
        ArticleMetricEntity entity = existing == null ? new ArticleMetricEntity() : existing;
        entity.setPath(result.path());
        if (result.pageviewCount() != null) {
            entity.setPageviewCount(result.pageviewCount());
        }
        if (result.commentCount() != null) {
            entity.setCommentCount(result.commentCount());
        }
        entity.setSyncedAt(appTimeProvider.nowOffsetDateTime());
        entity.setSourceStatus(result.sourceStatus());
        entity.setLastError(result.errorMessage());
        return entity;
    }

    private String errorMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
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
            Integer pageviewCount,
            Integer commentCount,
            String sourceStatus,
            String errorMessage,
            boolean success
    ) {
        private static MetricFetchResult failed(String path, String error) {
            return new MetricFetchResult(path, null, null, "failed", error, false);
        }
    }

    private record SyncStatus(
            String trigger,
            String startedAt,
            String finishedAt,
            int successCount,
            int failureCount,
            String lastError
    ) {
        private static SyncStatus initial() {
            return new SyncStatus("", "", "", 0, 0, "");
        }
    }
}
