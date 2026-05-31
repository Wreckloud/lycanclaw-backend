package com.lycanclaw.backend.recommendation.service;

import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.recommendation.entity.RecommendationMetricEntity;
import com.lycanclaw.backend.recommendation.repository.RecommendationMetricRepository;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 推荐聚合任务服务。
 * 周期拉取 Waline 指标并落库，向推荐接口提供可快速读取的热度快照。
 * @author Wreckloud
 * @since 2026-05-31
 */
@Service
public class RecommendationAggregationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationAggregationService.class);

    private final RecommendationSourceService sourceService;
    private final RecommendationMetricRepository metricRepository;
    private final WalineGatewayClient walineGatewayClient;
    private final RecommendationProperties properties;
    private final AppTimeProvider appTimeProvider;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<AggregationRunStatus> latestStatus = new AtomicReference<>(AggregationRunStatus.initial());

    public RecommendationAggregationService(
            RecommendationSourceService sourceService,
            RecommendationMetricRepository metricRepository,
            WalineGatewayClient walineGatewayClient,
            RecommendationProperties properties,
            AppTimeProvider appTimeProvider
    ) {
        this.sourceService = sourceService;
        this.metricRepository = metricRepository;
        this.walineGatewayClient = walineGatewayClient;
        this.properties = properties;
        this.appTimeProvider = appTimeProvider;
    }

    /**
     * 应用启动后异步触发首轮聚合，避免第一次前台访问时等待统计。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmupOnStartup() {
        if (!properties.isStartupWarmupEnabled()) {
            log.info("recommendation aggregation startup warmup disabled");
            return;
        }
        triggerAsyncAggregation("startup");
    }

    /**
     * 每 5 分钟触发一次聚合任务。
     */
    @Scheduled(fixedDelayString = "${lycan.recommendation.aggregation-interval-millis:300000}")
    public void runScheduledAggregation() {
        triggerAsyncAggregation("scheduled");
    }

    /**
     * 手动或调度触发异步聚合。
     */
    public Map<String, Object> triggerAsyncAggregation(String trigger) {
        if (!running.compareAndSet(false, true)) {
            Map<String, Object> status = snapshotState();
            return Map.of(
                    "accepted", false,
                    "trigger", trigger,
                    "message", "推荐聚合任务正在执行",
                    "status", status
            );
        }

        Thread worker = new Thread(() -> executeAggregation(trigger), "recommendation-aggregation");
        worker.setDaemon(true);
        worker.start();

        Map<String, Object> status = snapshotState();
        return Map.of(
                "accepted", true,
                "trigger", trigger,
                "message", "推荐聚合任务已受理",
                "status", status
        );
    }

    /**
     * 按 URL 读取聚合指标，供推荐服务拼装返回结果。
     */
    public Map<String, RecommendationMetricEntity> loadMetricsByUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Map.of();
        }
        return metricRepository.findAllByUrlIn(urls).stream()
                .collect(Collectors.toMap(RecommendationMetricEntity::getUrl, metric -> metric, (left, right) -> left));
    }

    /**
     * 返回聚合快照状态，供管理端展示任务健康度。
     */
    public Map<String, Object> snapshotState() {
        AggregationRunStatus status = latestStatus.get();
        long freshWindowMillis = Math.max(60_000L, properties.getSnapshotFreshSeconds() * 1000L);
        long nowMillis = Instant.now().toEpochMilli();
        boolean hasSnapshot = status.lastSuccessEpochMilli() > 0 && status.metricsCount() > 0;
        boolean expired = !hasSnapshot || nowMillis - status.lastSuccessEpochMilli() > freshWindowMillis;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("running", running.get());
        payload.put("hasSnapshot", hasSnapshot);
        payload.put("expired", expired);
        payload.put("metricsCount", status.metricsCount());
        payload.put("lastTrigger", status.lastTrigger());
        payload.put("lastStartedAt", appTimeProvider.toOffsetString(toInstant(status.lastStartedEpochMilli())));
        payload.put("lastCompletedAt", appTimeProvider.toOffsetString(toInstant(status.lastCompletedEpochMilli())));
        payload.put("lastSuccessAt", appTimeProvider.toOffsetString(toInstant(status.lastSuccessEpochMilli())));
        payload.put("lastDurationMs", status.lastDurationMs());
        payload.put("lastCandidateCount", status.lastCandidateCount());
        payload.put("lastSuccessCount", status.lastSuccessCount());
        payload.put("lastFailedCount", status.lastFailedCount());
        payload.put("lastError", status.lastError());
        payload.put("freshWindowSeconds", properties.getSnapshotFreshSeconds());
        payload.put("checkedAt", appTimeProvider.nowOffsetString());
        return payload;
    }

    private void executeAggregation(String trigger) {
        long startedAt = Instant.now().toEpochMilli();
        AggregationRunStatus previous = latestStatus.get();
        latestStatus.set(previous.start(trigger, startedAt));

        log.info("recommendation aggregation started, trigger={}", trigger);
        try {
            List<RecommendationSourceService.RecommendationCandidate> candidates = sourceService.loadCandidates();
            FetchBatchResult fetchResult = fetchMetrics(candidates);

            if (!fetchResult.entities().isEmpty()) {
                metricRepository.saveAll(fetchResult.entities());
            }

            long completedAt = Instant.now().toEpochMilli();
            long metricsCount = metricRepository.count();
            latestStatus.set(previous.success(
                    trigger,
                    startedAt,
                    completedAt,
                    candidates.size(),
                    fetchResult.successCount(),
                    fetchResult.failedCount(),
                    metricsCount,
                    fetchResult.lastError()
            ));
            log.info(
                    "recommendation aggregation finished, trigger={}, candidates={}, success={}, failed={}, durationMs={}",
                    trigger,
                    candidates.size(),
                    fetchResult.successCount(),
                    fetchResult.failedCount(),
                    completedAt - startedAt
            );
        } catch (Exception ex) {
            long completedAt = Instant.now().toEpochMilli();
            latestStatus.set(previous.failure(trigger, startedAt, completedAt, errorMessage(ex)));
            log.error("recommendation aggregation failed, trigger={}", trigger, ex);
        } finally {
            running.set(false);
        }
    }

    private FetchBatchResult fetchMetrics(List<RecommendationSourceService.RecommendationCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new FetchBatchResult(List.of(), 0, 0, "");
        }

        int parallelism = Math.max(1, properties.getAggregationFetchParallelism());
        int timeoutSeconds = Math.max(2, properties.getAggregationFetchTimeoutSeconds());

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<MetricFetchResult>> futures = new ArrayList<>(candidates.size());
            for (RecommendationSourceService.RecommendationCandidate candidate : candidates) {
                Callable<MetricFetchResult> task = () -> fetchSingleMetric(candidate);
                futures.add(executor.submit(task));
            }

            List<RecommendationMetricEntity> entities = new ArrayList<>(candidates.size());
            int success = 0;
            int failed = 0;
            String lastError = "";

            for (int i = 0; i < candidates.size(); i++) {
                RecommendationSourceService.RecommendationCandidate candidate = candidates.get(i);
                MetricFetchResult result;
                try {
                    result = futures.get(i).get(timeoutSeconds, TimeUnit.SECONDS);
                } catch (TimeoutException ex) {
                    result = MetricFetchResult.timeout(candidate.url());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    result = MetricFetchResult.failed(candidate.url(), "fetch interrupted");
                } catch (ExecutionException ex) {
                    result = MetricFetchResult.failed(candidate.url(), errorMessage(ex));
                }

                RecommendationMetricEntity entity = toEntity(result);
                entities.add(entity);

                if (result.success()) {
                    success++;
                } else {
                    failed++;
                    lastError = result.errorMessage();
                }
            }

            return new FetchBatchResult(entities, success, failed, lastError);
        } finally {
            executor.shutdownNow();
        }
    }

    private MetricFetchResult fetchSingleMetric(RecommendationSourceService.RecommendationCandidate candidate) {
        List<String> errors = new ArrayList<>();
        int pageviewCount = 0;
        int commentCount = 0;

        try {
            pageviewCount = Math.max(0, walineGatewayClient.fetchPageview(candidate.url()));
        } catch (Exception ex) {
            errors.add("pageview: " + errorMessage(ex));
        }

        try {
            commentCount = Math.max(0, walineGatewayClient.fetchCommentCount(candidate.url()));
        } catch (Exception ex) {
            errors.add("comment: " + errorMessage(ex));
        }

        double score = pageviewCount * properties.getScore().getPageviewWeight()
                + commentCount * properties.getScore().getCommentWeight();

        if (errors.isEmpty()) {
            return MetricFetchResult.success(candidate.url(), pageviewCount, commentCount, roundScore(score));
        }
        return MetricFetchResult.partial(candidate.url(), pageviewCount, commentCount, roundScore(score), String.join("; ", errors));
    }

    private RecommendationMetricEntity toEntity(MetricFetchResult result) {
        RecommendationMetricEntity entity = new RecommendationMetricEntity();
        entity.setUrl(result.url());
        entity.setPageviewCount(result.pageviewCount());
        entity.setCommentCount(result.commentCount());
        entity.setHotScore(result.hotScore());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
        entity.setSourceStatus(result.sourceStatus());
        entity.setLastError(result.errorMessage());
        return entity;
    }

    private double roundScore(double score) {
        return Math.round(score * 100.0D) / 100.0D;
    }

    private String errorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }

    private Instant toInstant(long epochMilli) {
        return epochMilli <= 0 ? null : Instant.ofEpochMilli(epochMilli);
    }

    private record FetchBatchResult(
            List<RecommendationMetricEntity> entities,
            int successCount,
            int failedCount,
            String lastError
    ) {
    }

    private record MetricFetchResult(
            String url,
            int pageviewCount,
            int commentCount,
            double hotScore,
            String sourceStatus,
            String errorMessage,
            boolean success
    ) {
        static MetricFetchResult success(String url, int pageviewCount, int commentCount, double hotScore) {
            return new MetricFetchResult(url, pageviewCount, commentCount, hotScore, "ok", "", true);
        }

        static MetricFetchResult partial(String url, int pageviewCount, int commentCount, double hotScore, String errorMessage) {
            return new MetricFetchResult(url, pageviewCount, commentCount, hotScore, "partial_error", errorMessage, false);
        }

        static MetricFetchResult timeout(String url) {
            return failed(url, "fetch timeout");
        }

        static MetricFetchResult failed(String url, String errorMessage) {
            return new MetricFetchResult(url, 0, 0, 0.0D, "failed", errorMessage, false);
        }
    }

    private record AggregationRunStatus(
            String lastTrigger,
            long lastStartedEpochMilli,
            long lastCompletedEpochMilli,
            long lastSuccessEpochMilli,
            long lastDurationMs,
            int lastCandidateCount,
            int lastSuccessCount,
            int lastFailedCount,
            long metricsCount,
            String lastError
    ) {
        static AggregationRunStatus initial() {
            return new AggregationRunStatus("", 0L, 0L, 0L, 0L, 0, 0, 0, 0L, "");
        }

        AggregationRunStatus start(String trigger, long startedAt) {
            return new AggregationRunStatus(
                    trigger,
                    startedAt,
                    lastCompletedEpochMilli,
                    lastSuccessEpochMilli,
                    lastDurationMs,
                    lastCandidateCount,
                    lastSuccessCount,
                    lastFailedCount,
                    metricsCount,
                    lastError
            );
        }

        AggregationRunStatus success(
                String trigger,
                long startedAt,
                long completedAt,
                int candidateCount,
                int successCount,
                int failedCount,
                long metricCount,
                String error
        ) {
            return new AggregationRunStatus(
                    trigger,
                    startedAt,
                    completedAt,
                    completedAt,
                    Math.max(0L, completedAt - startedAt),
                    candidateCount,
                    successCount,
                    failedCount,
                    metricCount,
                    error == null ? "" : error
            );
        }

        AggregationRunStatus failure(String trigger, long startedAt, long completedAt, String error) {
            return new AggregationRunStatus(
                    trigger,
                    startedAt,
                    completedAt,
                    lastSuccessEpochMilli,
                    Math.max(0L, completedAt - startedAt),
                    lastCandidateCount,
                    lastSuccessCount,
                    lastFailedCount,
                    metricsCount,
                    error == null ? "" : error
            );
        }
    }
}
