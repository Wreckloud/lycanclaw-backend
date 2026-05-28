package com.lycanclaw.backend.admin.service;

import com.lycanclaw.backend.recommendation.service.RecommendationService;
import com.lycanclaw.backend.tag.service.TagService;
import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.common.model.HealthLevel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端内容治理服务
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class AdminGovernanceService {

    private final RecommendationService recommendationService;
    private final TagService tagService;
    private final OpsCheckService opsCheckService;
    private final AppTimeProvider appTimeProvider;

    public AdminGovernanceService(
            RecommendationService recommendationService,
            TagService tagService,
            OpsCheckService opsCheckService,
            AppTimeProvider appTimeProvider
    ) {
        this.recommendationService = recommendationService;
        this.tagService = tagService;
        this.opsCheckService = opsCheckService;
        this.appTimeProvider = appTimeProvider;
    }

    /**
     * 手动触发推荐缓存重建，并返回动作执行快照。
     */
    public Map<String, Object> rebuildRecommendations() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "recommendations.rebuild");
        payload.put("result", recommendationService.forceRebuildCache());
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
     * - green：关键依赖正常；
     * - yellow：缓存缺失/过期或部分依赖异常；
     * - red：关键文件缺失或服务不可用。
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
        Map<String, Object> tagPosts = asMap(sync.get("tagPostsJson"));

        Map<String, Object> recommendationCache = recommendationService.cacheState();
        Map<String, Object> tagCache = tagService.cacheState();

        HealthLevel level = computeLevel(waline, ncm, posts, tagPosts, recommendationCache, tagCache);

        return Map.of(
                "level", level,
                "checkedAt", checks.getOrDefault("checkedAt", ""),
                "services", services,
                "sync", sync,
                "caches", Map.of(
                        "recommendation", recommendationCache,
                        "tag", tagCache
                )
        );
    }

    private HealthLevel computeLevel(
            Map<String, Object> waline,
            Map<String, Object> ncm,
            Map<String, Object> posts,
            Map<String, Object> tagPosts,
            Map<String, Object> recommendationCache,
            Map<String, Object> tagCache
    ) {
        boolean walineOk = asBoolean(waline.get("ok"));
        boolean ncmOk = asBoolean(ncm.get("ok"));
        boolean postsExists = asBoolean(posts.get("exists"));
        boolean tagPostsExists = asBoolean(tagPosts.get("exists"));

        if (!postsExists || !tagPostsExists || !walineOk) {
            return HealthLevel.RED;
        }

        boolean recommendationExpired = asBoolean(recommendationCache.get("expired"));
        boolean tagExpired = asBoolean(tagCache.get("expired"));
        boolean recommendationMissing = !asBoolean(recommendationCache.get("hasCache"));
        boolean tagMissing = !asBoolean(tagCache.get("hasCache"));

        if (!ncmOk || recommendationExpired || tagExpired || recommendationMissing || tagMissing) {
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
