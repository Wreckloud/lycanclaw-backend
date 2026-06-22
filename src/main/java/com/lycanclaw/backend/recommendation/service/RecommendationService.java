package com.lycanclaw.backend.recommendation.service;

import com.lycanclaw.backend.content.service.ArticleCatalogService;
import com.lycanclaw.backend.common.path.WebPathNormalizer;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.recommendation.dto.RecommendationCandidatePageDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationPostDto;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.service.ArticleMetricService;
import com.lycanclaw.backend.stats.service.ArticleMetricSyncService;
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

@Service
public class RecommendationService {

    private final ArticleCatalogService articleCatalogService;
    private final ArticleMetricService articleMetricService;
    private final ArticleMetricSyncService articleMetricSyncService;
    private final RecommendationRuleService recommendationRuleService;
    private final RecommendationProperties properties;

    public RecommendationService(
            ArticleCatalogService articleCatalogService,
            ArticleMetricService articleMetricService,
            ArticleMetricSyncService articleMetricSyncService,
            RecommendationRuleService recommendationRuleService,
            RecommendationProperties properties
    ) {
        this.articleCatalogService = articleCatalogService;
        this.articleMetricService = articleMetricService;
        this.articleMetricSyncService = articleMetricSyncService;
        this.recommendationRuleService = recommendationRuleService;
        this.properties = properties;
    }

    public List<RecommendationPostDto> listRecommendations(String excludePath, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String normalizedExcludePath = normalizePath(excludePath);
        List<ArticleCatalogService.ArticleCatalogItem> allArticles = articleCatalogService.loadPublishedThoughts();
        List<ArticleCatalogService.ArticleCatalogItem> automaticCandidates = allArticles.stream()
                .limit(Math.max(1, properties.getMaxCandidatePosts()))
                .toList();
        Map<String, ArticleCatalogService.ArticleCatalogItem> catalogByPath = allArticles.stream()
                .collect(Collectors.toMap(
                        ArticleCatalogService.ArticleCatalogItem::path,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, ArticleMetricEntity> metrics = articleMetricService.loadEntities(
                allArticles.stream().map(ArticleCatalogService.ArticleCatalogItem::path).toList()
        );
        RecommendationManualConfigDto rules = recommendationRuleService.read();
        Set<String> excluded = new HashSet<>(rules.excludedUrls());
        List<RecommendationPostDto> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String path : rules.manualUrls()) {
            ArticleCatalogService.ArticleCatalogItem item = catalogByPath.get(path);
            if (item == null || path.equals(normalizedExcludePath) || !seen.add(path)) {
                continue;
            }
            result.add(toRecommendation(item, metrics.get(path), true));
            if (result.size() >= safeLimit) {
                return result;
            }
        }

        List<RecommendationPostDto> automatic = automaticCandidates.stream()
                .map(item -> toRecommendation(item, metrics.get(item.path()), false))
                .sorted(Comparator
                        .comparingDouble(RecommendationPostDto::hotScore).reversed()
                        .thenComparing(Comparator.comparingLong(
                                (RecommendationPostDto post) -> parseDateEpoch(post.date())
                        ).reversed()))
                .toList();
        for (RecommendationPostDto post : automatic) {
            if (post.url().equals(normalizedExcludePath)
                    || excluded.contains(post.url())
                    || !seen.add(post.url())) {
                continue;
            }
            result.add(post);
            if (result.size() >= safeLimit) {
                break;
            }
        }
        return result;
    }

    public RecommendationManualConfigDto getManualConfig() {
        return recommendationRuleService.read();
    }

    public RecommendationManualConfigDto updateManualConfig(List<String> manualUrls, List<String> excludedUrls) {
        return recommendationRuleService.update(manualUrls, excludedUrls);
    }

    public RecommendationCandidatePageDto listCandidates(String keyword, int page, int pageSize) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        List<ArticleCatalogService.ArticleCatalogItem> articles = articleCatalogService.loadPublishedThoughts().stream()
                .limit(Math.max(1, properties.getMaxCandidatePosts()))
                .toList();
        Map<String, ArticleMetricEntity> metrics = articleMetricService.loadEntities(
                articles.stream().map(ArticleCatalogService.ArticleCatalogItem::path).toList()
        );
        List<RecommendationPostDto> candidates = articles.stream()
                .map(item -> toRecommendation(item, metrics.get(item.path()), false))
                .filter(item -> normalizedKeyword.isBlank()
                        || item.title().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || item.url().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(Comparator
                        .comparingLong((RecommendationPostDto item) -> parseDateEpoch(item.date())).reversed()
                        .thenComparing(RecommendationPostDto::title, String.CASE_INSENSITIVE_ORDER))
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

    public Map<String, Object> forceRebuildCache() {
        return articleMetricSyncService.triggerAsyncSync("manual");
    }

    public Map<String, Object> cacheState() {
        Map<String, Object> payload = new LinkedHashMap<>(articleMetricSyncService.snapshotState());
        payload.put("maxCandidatePosts", properties.getMaxCandidatePosts());
        payload.put("pageviewWeight", properties.getScore().getPageviewWeight());
        payload.put("commentWeight", properties.getScore().getCommentWeight());
        return payload;
    }

    private RecommendationPostDto toRecommendation(
            ArticleCatalogService.ArticleCatalogItem item,
            ArticleMetricEntity metric,
            boolean manualPinned
    ) {
        int pageviewCount = metric == null ? 0 : Math.max(0, metric.getPageviewCount());
        int commentCount = metric == null ? 0 : Math.max(0, metric.getCommentCount());
        double hotScore = pageviewCount * properties.getScore().getPageviewWeight()
                + commentCount * properties.getScore().getCommentWeight();
        return new RecommendationPostDto(
                item.path(),
                item.title(),
                item.description(),
                item.date(),
                item.tags(),
                pageviewCount,
                commentCount,
                Math.round(hotScore * 100.0D) / 100.0D,
                manualPinned
        );
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return WebPathNormalizer.normalize(value);
    }

    private long parseDateEpoch(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
            return LocalDateTime.parse(trimmed, formatter).toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }
}
