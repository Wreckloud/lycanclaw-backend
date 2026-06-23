package com.lycanclaw.backend.admin.service;

import com.lycanclaw.backend.stats.service.ArticleMetricSyncService;
import com.lycanclaw.backend.tag.service.TagService;
import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.common.model.HealthLevel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理治理服务。
 * 提供文章指标同步、标签缓存刷新与治理状态分级能力。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class AdminGovernanceService {

    private final ArticleMetricSyncService articleMetricSyncService;
    private final TagService tagService;
    private final OpsCheckService opsCheckService;
    private final AppTimeProvider appTimeProvider;

    public AdminGovernanceService(
            ArticleMetricSyncService articleMetricSyncService,
            TagService tagService,
            OpsCheckService opsCheckService,
            AppTimeProvider appTimeProvider
    ) {
        this.articleMetricSyncService = articleMetricSyncService;
        this.tagService = tagService;
        this.opsCheckService = opsCheckService;
        this.appTimeProvider = appTimeProvider;
    }

    /**
     * 手动触发文章指标同步，并返回受理状态。
     */
    public Map<String, Object> syncArticleMetrics() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "article-metrics.sync");
        payload.put("result", articleMetricSyncService.triggerAsyncSync("manual"));
        payload.put("checkedAt", appTimeProvider.nowOffsetString());
        return payload;
    }

    /**
     * 手动刷新标签缓存，并返回动作执行快照。
     */
    public Map<String, Object> refreshTagsCache() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "tags.refresh");
        payload.put("result", tagService.refreshCache());
        payload.put("checkedAt", appTimeProvider.nowOffsetString());
        return payload;
    }

    /**
     * 数据同步状态分级：
     * - green：关键依赖和最近一次指标同步正常；
     * - yellow：音乐上游或部分文章指标同步异常；
     * - red：内容索引缺失或 Waline 不可用。
     */
    public Map<String, Object> syncStatus() {
        Map<String, Object> checks = opsCheckService.collectChecks();
        return syncStatus(checks);
    }

    /**
     * 允许调用方复用同一份检查快照，避免重复探测上游服务。
     */
    public Map<String, Object> syncStatus(Map<String, Object> checks) {
        Map<String, Object> services = asMap(checks.get("services"));
        Map<String, Object> sync = asMap(checks.get("sync"));

        Map<String, Object> waline = asMap(services.get("waline"));
        Map<String, Object> ncm = asMap(services.get("ncmUpstream"));
        Map<String, Object> posts = asMap(sync.get("postsJson"));
        Map<String, Object> knowledge = asMap(sync.get("knowledgeStatsJson"));
        Map<String, Object> articleMetrics = articleMetricSyncService.snapshotState();

        HealthLevel level = computeLevel(waline, ncm, posts, knowledge, articleMetrics);

        return Map.of(
                "level", level,
                "checkedAt", checks.getOrDefault("checkedAt", ""),
                "services", services,
                "sync", sync,
                "jobs", Map.of(
                        "articleMetrics", articleMetrics
                )
        );
    }

    private HealthLevel computeLevel(
            Map<String, Object> waline,
            Map<String, Object> ncm,
            Map<String, Object> posts,
            Map<String, Object> knowledge,
            Map<String, Object> articleMetrics
    ) {
        boolean walineOk = asBoolean(waline.get("ok"));
        boolean ncmOk = asBoolean(ncm.get("ok"));
        boolean postsExists = asBoolean(posts.get("exists"));
        boolean knowledgeExists = asBoolean(knowledge.get("exists"));

        if (!postsExists || !knowledgeExists || !walineOk) {
            return HealthLevel.RED;
        }

        int metricFailures = articleMetrics.get("failureCount") instanceof Number number
                ? number.intValue()
                : 0;
        boolean metricError = !String.valueOf(articleMetrics.getOrDefault("lastError", "")).isBlank();

        if (!ncmOk || metricFailures > 0 || metricError) {
            return HealthLevel.YELLOW;
        }
        return HealthLevel.GREEN;
    }

    /**
     * 仅在 value 是 Boolean.TRUE 时返回 true，其他类型全部按 false 处理。
     */
    private boolean asBoolean(Object value) {
        return value instanceof Boolean b && b;
    }

    /**
     * 聚合检查结果来自多模块 Map，类型不可信，统一做安全转换。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
