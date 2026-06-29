package com.lycanclaw.backend.analytics.repository;

import com.lycanclaw.backend.analytics.entity.AnalyticsVisitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 页面访问统计仓储。
 * 提供访问记录创建、停留时间结算和后台聚合查询需要的基础读写能力。
 * @author Wreckloud
 * @since 2026-06-04
 */
public interface AnalyticsVisitRepository extends JpaRepository<AnalyticsVisitEntity, Long> {

    Optional<AnalyticsVisitEntity> findByVisitId(String visitId);

    List<AnalyticsVisitEntity> findByStartedAtAfter(OffsetDateTime startedAt);

    List<AnalyticsVisitEntity> findByPathAndStartedAtAfter(String path, OffsetDateTime startedAt);

    List<AnalyticsVisitEntity> findByVisitorIdAndStartedAtAfter(String visitorId, OffsetDateTime startedAt);
}
