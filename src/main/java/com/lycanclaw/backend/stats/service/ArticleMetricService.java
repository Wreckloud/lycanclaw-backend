package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.common.path.WebPathNormalizer;
import com.lycanclaw.backend.stats.dto.ArticleMetricDto;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.repository.ArticleMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章指标快照查询与更新服务。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
@Service
public class ArticleMetricService {

    private final ArticleMetricRepository repository;

    public ArticleMetricService(ArticleMetricRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ArticleMetricDto find(String path) {
        String normalized = normalizePath(path);
        return repository.findById(normalized)
                .map(this::toDto)
                .orElseGet(() -> emptyMetric(normalized));
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
        repository.save(entity);
    }

    private ArticleMetricDto toDto(ArticleMetricEntity entity) {
        return new ArticleMetricDto(
                entity.getPath(),
                entity.getPageviewCount(),
                entity.getCommentCount()
        );
    }

    private ArticleMetricDto emptyMetric(String path) {
        return new ArticleMetricDto(path, 0, 0);
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("文章路径不能为空");
        }
        return WebPathNormalizer.normalize(value);
    }
}
