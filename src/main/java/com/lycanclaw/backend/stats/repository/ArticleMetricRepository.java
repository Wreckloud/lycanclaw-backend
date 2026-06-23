package com.lycanclaw.backend.stats.repository;

import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 文章指标快照仓库。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
public interface ArticleMetricRepository extends JpaRepository<ArticleMetricEntity, String> {

    List<ArticleMetricEntity> findAllByPathIn(Collection<String> paths);
}
