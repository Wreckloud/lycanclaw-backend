package com.lycanclaw.backend.recommendation.service;

import com.lycanclaw.backend.content.service.ContentCatalogService;
import com.lycanclaw.backend.common.path.WebPathNormalizer;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.recommendation.dto.RecommendationCandidatePageDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationPostDto;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.service.ArticleMetricService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * 按手动规则和文章热度生成推荐列表。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
@Service
public class RecommendationService {

    private static final DateTimeFormatter CONTENT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);

    private final ContentCatalogService contentCatalogService;
    private final ArticleMetricService articleMetricService;
    private final RecommendationRuleService recommendationRuleService;
    private final RecommendationProperties properties;

    public RecommendationService(
            ContentCatalogService contentCatalogService,
            ArticleMetricService articleMetricService,
            RecommendationRuleService recommendationRuleService,
            RecommendationProperties properties
    ) {
        this.contentCatalogService = contentCatalogService;
        this.articleMetricService = articleMetricService;
        this.recommendationRuleService = recommendationRuleService;
        this.properties = properties;
    }

    public List<RecommendationPostDto> listRecommendations(String excludePath, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String normalizedExcludePath = normalizePath(excludePath);
        List<ContentCatalogService.ContentItem> allArticles = contentCatalogService.loadPublishedThoughts();
        List<ContentCatalogService.ContentItem> automaticCandidates = allArticles.stream()
                .limit(Math.max(1, properties.getMaxCandidatePosts()))
                .toList();
        Map<String, ContentCatalogService.ContentItem> catalogByPath = allArticles.stream()
                .collect(Collectors.toMap(
                        ContentCatalogService.ContentItem::path,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, ArticleMetricEntity> metrics = articleMetricService.loadEntities(
                allArticles.stream().map(ContentCatalogService.ContentItem::path).toList()
        );
        RecommendationManualConfigDto rules = recommendationRuleService.read();
        Set<String> excluded = new HashSet<>(rules.excludedUrls());
        List<RecommendationPostDto> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 先按后台配置顺序放入手动项，再由自动推荐补足剩余位置。
        for (String path : rules.manualUrls()) {
            ContentCatalogService.ContentItem item = catalogByPath.get(path);
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
        List<ContentCatalogService.ContentItem> articles = contentCatalogService.loadPublishedThoughts().stream()
                .limit(Math.max(1, properties.getMaxCandidatePosts()))
                .toList();
        Map<String, ArticleMetricEntity> metrics = articleMetricService.loadEntities(
                articles.stream().map(ContentCatalogService.ContentItem::path).toList()
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

    private RecommendationPostDto toRecommendation(
            ContentCatalogService.ContentItem item,
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
        return LocalDateTime.parse(value, CONTENT_DATE_FORMATTER)
                .toInstant(ZoneOffset.ofHours(8))
                .toEpochMilli();
    }
}
