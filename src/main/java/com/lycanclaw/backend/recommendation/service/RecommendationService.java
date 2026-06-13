package com.lycanclaw.backend.recommendation.service;

import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationCandidatePageDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationPostDto;
import com.lycanclaw.backend.recommendation.entity.RecommendationMetricEntity;
import com.lycanclaw.backend.runtimeconfig.service.RuntimeConfigService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐计算服务。
 * 组合手动推荐与聚合热度快照，向前台输出推荐阅读列表。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class RecommendationService {

    private final RecommendationSourceService sourceService;
    private final RecommendationManualConfigService manualConfigService;
    private final RecommendationAggregationService aggregationService;
    private final RecommendationProperties properties;
    private final RuntimeConfigService runtimeConfigService;

    public RecommendationService(
            RecommendationSourceService sourceService,
            RecommendationManualConfigService manualConfigService,
            RecommendationAggregationService aggregationService,
            RecommendationProperties properties,
            RuntimeConfigService runtimeConfigService
    ) {
        this.sourceService = sourceService;
        this.manualConfigService = manualConfigService;
        this.aggregationService = aggregationService;
        this.properties = properties;
        this.runtimeConfigService = runtimeConfigService;
    }

    /**
     * 获取推荐结果：手动置顶优先，剩余位置按热门分补齐。
     */
    public List<RecommendationPostDto> listRecommendations(String excludePath, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String normalizedExcludePath = normalizeUrl(excludePath);

        RecommendationManualConfigDto manualConfig = manualConfigService.read();
        Set<String> excludedUrls = new HashSet<>(manualConfig.excludedUrls());
        List<RecommendationPostDto> hotPosts = buildHotPostsFromMetrics();
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
                    post = buildRecommendation(candidate, null);
                } else {
                    post = buildManualFallback(manualUrl);
                }
            }
            if (seen.contains(post.url())) {
                continue;
            }
            result.add(toManualPinned(post));
            seen.add(post.url());
            if (result.size() >= safeLimit) {
                return result;
            }
        }

        for (RecommendationPostDto post : hotPosts) {
            if (post.url().equals(normalizedExcludePath)
                    || seen.contains(post.url())
                    || excludedUrls.contains(post.url())) {
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
     * 更新手动推荐配置。
     */
    public RecommendationManualConfigDto updateManualConfig(List<String> manualUrls, List<String> excludedUrls) {
        return manualConfigService.update(manualUrls, excludedUrls);
    }

    /**
     * 获取管理端候选文章列表（按发布时间倒序）。
     */
    public List<RecommendationPostDto> listCandidates(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, runtimeConfigService.maxCandidatePosts()));

        List<RecommendationSourceService.RecommendationCandidate> candidates = sourceService.loadCandidates().stream()
                .sorted(Comparator
                        .comparingLong((RecommendationSourceService.RecommendationCandidate candidate) -> parseDateEpoch(candidate.date()))
                        .reversed()
                        .thenComparing(RecommendationSourceService.RecommendationCandidate::title, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<String, RecommendationMetricEntity> metricsByUrl = aggregationService.loadMetricsByUrls(
                candidates.stream().map(RecommendationSourceService.RecommendationCandidate::url).toList()
        );

        return candidates.stream()
                .map(candidate -> buildRecommendation(candidate, metricsByUrl.get(candidate.url())))
                .limit(safeLimit)
                .toList();
    }

    /**
     * 按标题关键词和分页条件返回管理端候选文章。
     */
    public RecommendationCandidatePageDto listCandidates(String keyword, int page, int pageSize) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        List<RecommendationPostDto> candidates = listCandidates(runtimeConfigService.maxCandidatePosts()).stream()
                .filter(item -> normalizedKeyword.isBlank()
                        || item.title().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || item.url().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .toList();
        int totalPages = Math.max(1, (int) Math.ceil(candidates.size() / (double) safePageSize));
        int safePage = Math.max(1, Math.min(page, totalPages));
        int from = Math.min(candidates.size(), (safePage - 1) * safePageSize);
        int to = Math.min(candidates.size(), from + safePageSize);
        return new RecommendationCandidatePageDto(
                safePage,
                safePageSize,
                candidates.size(),
                totalPages,
                candidates.subList(from, to)
        );
    }

    /**
     * 手动触发推荐聚合任务，异步执行并立即返回受理状态。
     */
    public Map<String, Object> forceRebuildCache() {
        return aggregationService.triggerAsyncAggregation("manual");
    }

    /**
     * 返回当前推荐聚合快照状态，供管理端展示。
     */
    public Map<String, Object> cacheState() {
        Map<String, Object> aggregation = aggregationService.snapshotState();

        Map<String, Object> payload = new LinkedHashMap<>(aggregation);
        payload.put("hasCache", asBoolean(aggregation.get("hasSnapshot")));
        payload.put("size", asInt(aggregation.get("metricsCount")));
        payload.put("ttlSeconds", runtimeConfigService.snapshotFreshSeconds());
        return payload;
    }

    private List<RecommendationPostDto> buildHotPostsFromMetrics() {
        List<RecommendationSourceService.RecommendationCandidate> candidates = sourceService.loadCandidates();
        Map<String, RecommendationMetricEntity> metricsByUrl = aggregationService.loadMetricsByUrls(
                candidates.stream().map(RecommendationSourceService.RecommendationCandidate::url).toList()
        );

        List<RecommendationPostDto> posts = new ArrayList<>(candidates.size());
        for (RecommendationSourceService.RecommendationCandidate candidate : candidates) {
            posts.add(buildRecommendation(candidate, metricsByUrl.get(candidate.url())));
        }

        posts.sort(
                Comparator.comparingDouble(RecommendationPostDto::hotScore).reversed()
                        .thenComparing(Comparator.comparingLong(
                                (RecommendationPostDto post) -> parseDateEpoch(post.date())
                        ).reversed())
        );
        return posts;
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

    private RecommendationPostDto buildRecommendation(
            RecommendationSourceService.RecommendationCandidate candidate,
            RecommendationMetricEntity metric
    ) {
        int pageviewCount = metric == null ? 0 : Math.max(0, metric.getPageviewCount());
        int commentCount = metric == null ? 0 : Math.max(0, metric.getCommentCount());
        double hotScore = metric == null ? 0.0D : metric.getHotScore();

        return new RecommendationPostDto(
                candidate.url(),
                candidate.title(),
                candidate.description(),
                candidate.date(),
                candidate.tags(),
                pageviewCount,
                commentCount,
                hotScore,
                false
        );
    }

    private RecommendationPostDto buildManualFallback(String url) {
        String decoded = java.net.URLDecoder.decode(url, java.nio.charset.StandardCharsets.UTF_8);
        int slash = decoded.lastIndexOf('/');
        String filename = slash >= 0 ? decoded.substring(slash + 1) : decoded;
        String title = filename.endsWith(".html")
                ? filename.substring(0, filename.length() - ".html".length())
                : filename;
        return new RecommendationPostDto(
                url,
                title.isBlank() ? "未命名文章" : title,
                "",
                "",
                List.of(),
                0,
                0,
                0,
                false
        );
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

    private boolean asBoolean(Object value) {
        return value instanceof Boolean b && b;
    }

    private int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
