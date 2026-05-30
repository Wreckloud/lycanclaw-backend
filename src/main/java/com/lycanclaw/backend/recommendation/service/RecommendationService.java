package com.lycanclaw.backend.recommendation.service;

import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationPostDto;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐计算服务。
 * 计算推荐文章、维护推荐缓存并提供刷新能力。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class RecommendationService {

    private final RecommendationSourceService sourceService;
    private final RecommendationManualConfigService manualConfigService;
    private final WalineGatewayClient walineGatewayClient;
    private final RecommendationProperties properties;

    private volatile CachedHotSnapshot cachedHotSnapshot;

    public RecommendationService(
            RecommendationSourceService sourceService,
            RecommendationManualConfigService manualConfigService,
            WalineGatewayClient walineGatewayClient,
            RecommendationProperties properties
    ) {
        this.sourceService = sourceService;
        this.manualConfigService = manualConfigService;
        this.walineGatewayClient = walineGatewayClient;
        this.properties = properties;
    }

    /**
     * 获取推荐结果：手动置顶优先，剩余位置按热门分补齐。
     */
    public List<RecommendationPostDto> listRecommendations(String excludePath, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String normalizedExcludePath = normalizeUrl(excludePath);

        RecommendationManualConfigDto manualConfig = manualConfigService.read();
        List<RecommendationPostDto> hotPosts = getHotPosts();
        Map<String, RecommendationSourceService.RecommendationCandidate> candidateByUrl = sourceService.loadAllCandidates().stream()
                .collect(Collectors.toMap(
                        RecommendationSourceService.RecommendationCandidate::url,
                        candidate -> candidate,
                        (left, right) -> left
                ));

        Map<String, RecommendationPostDto> byUrl = hotPosts.stream()
                .collect(Collectors.toMap(RecommendationPostDto::url, post -> post, (left, right) -> left));

        List<RecommendationPostDto> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String manualUrl : manualConfig.manualUrls()) {
            if (manualUrl.equals(normalizedExcludePath)) {
                continue;
            }
            RecommendationPostDto post = byUrl.get(manualUrl);
            if (post == null) {
                RecommendationSourceService.RecommendationCandidate candidate = candidateByUrl.get(manualUrl);
                if (candidate != null) {
                    post = buildRecommendation(candidate);
                }
            }
            if (post == null || seen.contains(post.url())) {
                continue;
            }
            result.add(toManualPinned(post));
            seen.add(post.url());
            if (result.size() >= safeLimit) {
                return result;
            }
        }

        for (RecommendationPostDto post : hotPosts) {
            if (post.url().equals(normalizedExcludePath) || seen.contains(post.url())) {
                continue;
            }
            result.add(post);
            seen.add(post.url());
            if (result.size() >= safeLimit) {
                break;
            }
        }

        return result;
    }
    /**
     * 读取手动推荐配置。
     */
    public RecommendationManualConfigDto getManualConfig() {
        return manualConfigService.read();
    }

    /**
     * 更新手动推荐配置并清空热门缓存。
     */
    public RecommendationManualConfigDto updateManualConfig(List<String> manualUrls) {
        RecommendationManualConfigDto config = manualConfigService.update(manualUrls);
        cachedHotSnapshot = null;
        return config;
    }

    /**
     * 获取管理端候选文章列表（按发布时间倒序）。
     */
    public List<RecommendationPostDto> listCandidates(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, Math.max(1, properties.getMaxCandidatePosts())));
        Map<String, RecommendationPostDto> hotByUrl = getHotPosts().stream()
                .collect(Collectors.toMap(RecommendationPostDto::url, post -> post, (left, right) -> left));

        return sourceService.loadCandidates().stream()
                .sorted(Comparator
                        .comparingLong((RecommendationSourceService.RecommendationCandidate candidate) -> parseDateEpoch(candidate.date()))
                        .reversed()
                        .thenComparing(RecommendationSourceService.RecommendationCandidate::title, String.CASE_INSENSITIVE_ORDER))
                .map(candidate -> {
                    RecommendationPostDto hotPost = hotByUrl.get(candidate.url());
                    return hotPost != null ? hotPost : buildRecommendation(candidate);
                })
                .limit(safeLimit)
                .toList();
    }

    /**
     * 手动触发推荐缓存重算，返回最新缓存快照。
     */
    public synchronized Map<String, Object> forceRebuildCache() {
        List<RecommendationPostDto> rebuilt = rebuildHotPosts();
        long now = Instant.now().toEpochMilli();
        cachedHotSnapshot = new CachedHotSnapshot(now, rebuilt);

        return Map.of(
                "rebuiltAtEpochMilli", now,
                "totalCandidates", rebuilt.size(),
                "topUrls", rebuilt.stream().limit(5).map(RecommendationPostDto::url).toList()
        );
    }

    /**
     * 返回当前推荐缓存状态，供管理端展示。
     */
    public Map<String, Object> cacheState() {
        CachedHotSnapshot snapshot = cachedHotSnapshot;
        if (snapshot == null) {
            return Map.of(
                    "hasCache", false,
                    "expired", true,
                    "size", 0
            );
        }

        boolean expired = isCacheExpired(snapshot.generatedAtEpochMilli());
        return Map.of(
                "hasCache", true,
                "expired", expired,
                "size", snapshot.posts().size(),
                "generatedAtEpochMilli", snapshot.generatedAtEpochMilli(),
                "ttlSeconds", Math.max(5, properties.getCacheSeconds())
        );
    }

    private RecommendationPostDto toManualPinned(RecommendationPostDto post) {
        return new RecommendationPostDto(
                post.url(),
                post.title(),
                post.description(),
                post.date(),
                post.tags(),
                post.pageviewCount(),
                post.commentCount(),
                post.hotScore(),
                true
        );
    }

    private List<RecommendationPostDto> getHotPosts() {
        CachedHotSnapshot current = cachedHotSnapshot;
        if (current != null && !isCacheExpired(current.generatedAtEpochMilli())) {
            return current.posts();
        }

        synchronized (this) {
            CachedHotSnapshot latest = cachedHotSnapshot;
            if (latest != null && !isCacheExpired(latest.generatedAtEpochMilli())) {
                return latest.posts();
            }

            List<RecommendationPostDto> rebuilt = rebuildHotPosts();
            cachedHotSnapshot = new CachedHotSnapshot(Instant.now().toEpochMilli(), rebuilt);
            return rebuilt;
        }
    }

    private boolean isCacheExpired(long generatedAtEpochMilli) {
        int cacheSeconds = Math.max(5, properties.getCacheSeconds());
        long ttlMillis = cacheSeconds * 1000L;
        return Instant.now().toEpochMilli() - generatedAtEpochMilli > ttlMillis;
    }

    private List<RecommendationPostDto> rebuildHotPosts() {
        List<RecommendationSourceService.RecommendationCandidate> candidates = sourceService.loadCandidates();
        Map<String, RecommendationPostDto> hotByUrl = new HashMap<>();

        for (RecommendationSourceService.RecommendationCandidate candidate : candidates) {
            RecommendationPostDto post = buildRecommendation(candidate);
            hotByUrl.put(post.url(), post);
        }

        return hotByUrl.values().stream()
                .sorted(Comparator
                        .comparingDouble(RecommendationPostDto::hotScore).reversed()
                        .thenComparing(RecommendationPostDto::date, Comparator.reverseOrder()))
                .toList();
    }

    private RecommendationPostDto buildRecommendation(RecommendationSourceService.RecommendationCandidate candidate) {
        int pageviewCount = safeFetchPageview(candidate.url());
        int commentCount = safeFetchComment(candidate.url());

        double score = pageviewCount * properties.getScore().getPageviewWeight()
                + commentCount * properties.getScore().getCommentWeight();

        return new RecommendationPostDto(
                candidate.url(),
                candidate.title(),
                candidate.description(),
                candidate.date(),
                candidate.tags(),
                pageviewCount,
                commentCount,
                Math.round(score * 100.0) / 100.0,
                false
        );
    }

    private int safeFetchPageview(String url) {
        try {
            return Math.max(0, walineGatewayClient.fetchPageview(url));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int safeFetchComment(String url) {
        try {
            return Math.max(0, walineGatewayClient.fetchCommentCount(url));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private long parseDateEpoch(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // try next parser
        }
        try {
            return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // try next parser
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
            return LocalDateTime.parse(trimmed, formatter).toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // try next parser
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }

    private record CachedHotSnapshot(long generatedAtEpochMilli, List<RecommendationPostDto> posts) {
    }
}
