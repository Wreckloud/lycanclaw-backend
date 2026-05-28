package com.lycanclaw.backend.admin.service;

import com.lycanclaw.backend.recommendation.service.RecommendationService;
import com.lycanclaw.backend.tag.service.TagService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    public AdminGovernanceService(
            RecommendationService recommendationService,
            TagService tagService,
            OpsCheckService opsCheckService
    ) {
        this.recommendationService = recommendationService;
        this.tagService = tagService;
        this.opsCheckService = opsCheckService;
    }

    public Map<String, Object> rebuildRecommendations() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "recommendations.rebuild");
        payload.put("result", recommendationService.forceRebuildCache());
        payload.put("checkedAt", OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        return payload;
    }

    public Map<String, Object> refreshTagsCache() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "tags.refresh");
        payload.put("result", tagService.refreshCache());
        payload.put("checkedAt", OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
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
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) checks.getOrDefault("services", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> sync = (Map<String, Object>) checks.getOrDefault("sync", Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> waline = (Map<String, Object>) services.getOrDefault("waline", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> ncm = (Map<String, Object>) services.getOrDefault("ncmUpstream", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> posts = (Map<String, Object>) sync.getOrDefault("postsJson", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> tagPosts = (Map<String, Object>) sync.getOrDefault("tagPostsJson", Map.of());

        Map<String, Object> recommendationCache = recommendationService.cacheState();
        Map<String, Object> tagCache = tagService.cacheState();

        String level = computeLevel(waline, ncm, posts, tagPosts, recommendationCache, tagCache);

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

    private String computeLevel(
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
            return "red";
        }

        boolean recommendationExpired = asBoolean(recommendationCache.get("expired"));
        boolean tagExpired = asBoolean(tagCache.get("expired"));
        boolean recommendationMissing = !asBoolean(recommendationCache.get("hasCache"));
        boolean tagMissing = !asBoolean(tagCache.get("hasCache"));

        if (!ncmOk || recommendationExpired || tagExpired || recommendationMissing || tagMissing) {
            return "yellow";
        }
        return "green";
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean b && b;
    }
}
