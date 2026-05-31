package com.lycanclaw.backend.recommendation.repository;

import com.lycanclaw.backend.recommendation.entity.RecommendationMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 推荐聚合指标仓储。
 * 负责 recommendation_metrics 的读写与批量查询。
 * @author Wreckloud
 * @since 2026-05-31
 */
public interface RecommendationMetricRepository extends JpaRepository<RecommendationMetricEntity, String> {

    /**
     * 按 URL 批量读取聚合指标。
     */
    List<RecommendationMetricEntity> findAllByUrlIn(Collection<String> urls);

    /**
     * 统计指定时间之后更新过的指标数量。
     */
    long countByUpdatedAtAfter(OffsetDateTime updatedAt);
}
