package com.lycanclaw.backend.recommendation.service;

import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.common.path.WebPathNormalizer;
import com.lycanclaw.backend.content.service.ContentCatalogService;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.entity.RecommendationRuleEntity;
import com.lycanclaw.backend.recommendation.repository.RecommendationRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理手动推荐顺序和自动推荐排除规则。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
@Service
public class RecommendationRuleService {

    private final RecommendationRuleRepository repository;
    private final ContentCatalogService contentCatalogService;
    private final AppTimeProvider appTimeProvider;

    public RecommendationRuleService(
            RecommendationRuleRepository repository,
            ContentCatalogService contentCatalogService,
            AppTimeProvider appTimeProvider
    ) {
        this.repository = repository;
        this.contentCatalogService = contentCatalogService;
        this.appTimeProvider = appTimeProvider;
    }

    @Transactional(readOnly = true)
    public RecommendationManualConfigDto read() {
        Set<String> validPaths = publishedThoughtPaths();
        // 已删除文章的旧规则不再返回，下一次保存配置时会被全量覆盖清理。
        List<RecommendationRuleEntity> rules = repository.findAll().stream()
                .filter(rule -> validPaths.contains(rule.getPath()))
                .toList();
        List<String> manualUrls = rules.stream()
                .filter(rule -> rule.getManualRank() != null)
                .sorted(Comparator.comparingInt(RecommendationRuleEntity::getManualRank))
                .map(RecommendationRuleEntity::getPath)
                .toList();
        List<String> excludedUrls = rules.stream()
                .filter(RecommendationRuleEntity::isExcluded)
                .map(RecommendationRuleEntity::getPath)
                .sorted()
                .toList();
        String updatedAt = rules.stream()
                .map(RecommendationRuleEntity::getUpdatedAt)
                .max(Comparator.naturalOrder())
                .map(appTimeProvider::toOffsetString)
                .orElse(null);
        return new RecommendationManualConfigDto(manualUrls, excludedUrls, updatedAt);
    }

    @Transactional
    public RecommendationManualConfigDto update(List<String> manualUrls, List<String> excludedUrls) {
        Set<String> validPaths = publishedThoughtPaths();
        List<String> manual = normalize(manualUrls, validPaths);
        List<String> excluded = normalize(excludedUrls, validPaths).stream()
                .filter(path -> !manual.contains(path))
                .toList();
        OffsetDateTime now = appTimeProvider.nowOffsetDateTime();
        List<RecommendationRuleEntity> next = new ArrayList<>();

        for (int index = 0; index < manual.size(); index++) {
            RecommendationRuleEntity rule = new RecommendationRuleEntity();
            rule.setPath(manual.get(index));
            rule.setManualRank(index + 1);
            rule.setExcluded(false);
            rule.setUpdatedAt(now);
            next.add(rule);
        }
        for (String path : excluded) {
            RecommendationRuleEntity rule = new RecommendationRuleEntity();
            rule.setPath(path);
            rule.setManualRank(null);
            rule.setExcluded(true);
            rule.setUpdatedAt(now);
            next.add(rule);
        }

        repository.deleteAllInBatch();
        repository.saveAll(next);
        return new RecommendationManualConfigDto(manual, excluded, appTimeProvider.toOffsetString(now));
    }

    private Set<String> publishedThoughtPaths() {
        return contentCatalogService.loadPublishedThoughts().stream()
                .map(ContentCatalogService.ContentItem::path)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private List<String> normalize(List<String> paths, Set<String> validPaths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : paths) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String path = WebPathNormalizer.normalize(value);
            if (!validPaths.contains(path)) {
                throw new IllegalArgumentException("推荐文章不存在或未发布: " + path);
            }
            normalized.add(path);
        }
        return List.copyOf(normalized);
    }
}
