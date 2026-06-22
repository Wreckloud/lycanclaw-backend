package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.common.path.WebPathNormalizer;
import com.lycanclaw.backend.stats.dto.ArticleMetricDto;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.repository.ArticleMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ArticleMetricService {

    private final ArticleMetricRepository repository;
    private final AppTimeProvider appTimeProvider;

    public ArticleMetricService(ArticleMetricRepository repository, AppTimeProvider appTimeProvider) {
        this.repository = repository;
        this.appTimeProvider = appTimeProvider;
    }

    @Transactional(readOnly = true)
    public ArticleMetricDto find(String path) {
        String normalized = normalizePath(path);
        return repository.findById(normalized)
                .map(this::toDto)
                .orElseGet(() -> emptyMetric(normalized));
    }

    @Transactional(readOnly = true)
    public List<ArticleMetricDto> findBatch(List<String> paths) {
        List<String> normalized = paths.stream()
                .filter(Objects::nonNull)
                .map(this::normalizePath)
                .distinct()
                .toList();
        Map<String, ArticleMetricEntity> byPath = loadEntities(normalized);
        return normalized.stream()
                .map(path -> {
                    ArticleMetricEntity entity = byPath.get(path);
                    return entity == null ? emptyMetric(path) : toDto(entity);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, ArticleMetricEntity> loadEntities(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return Map.of();
        }
        Map<String, ArticleMetricEntity> result = new LinkedHashMap<>();
        for (ArticleMetricEntity entity : repository.findAllByPathIn(paths)) {
            result.put(entity.getPath(), entity);
        }
        return result;
    }

    @Transactional
    public void updatePageview(String path, int pageviewCount) {
        String normalized = normalizePath(path);
        ArticleMetricEntity entity = repository.findById(normalized).orElseGet(ArticleMetricEntity::new);
        entity.setPath(normalized);
        entity.setPageviewCount(Math.max(0, pageviewCount));
        entity.setCommentCount(Math.max(0, entity.getCommentCount()));
        entity.setSyncedAt(appTimeProvider.nowOffsetDateTime());
        entity.setSourceStatus("ok");
        entity.setLastError("");
        repository.save(entity);
    }

    private ArticleMetricDto toDto(ArticleMetricEntity entity) {
        return new ArticleMetricDto(
                entity.getPath(),
                entity.getPageviewCount(),
                entity.getCommentCount(),
                appTimeProvider.toOffsetString(entity.getSyncedAt())
        );
    }

    private ArticleMetricDto emptyMetric(String path) {
        return new ArticleMetricDto(path, 0, 0, null);
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("文章路径不能为空");
        }
        return WebPathNormalizer.normalize(value);
    }
}
