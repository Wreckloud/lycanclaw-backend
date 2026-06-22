package com.lycanclaw.backend.stats.repository;

import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArticleMetricRepository extends JpaRepository<ArticleMetricEntity, String> {

    List<ArticleMetricEntity> findAllByPathIn(Collection<String> paths);

    Optional<ArticleMetricEntity> findTopByOrderBySyncedAtDesc();
}
