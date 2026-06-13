package com.lycanclaw.backend.analytics.repository;

import com.lycanclaw.backend.analytics.entity.AnalyticsVisitorIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

/**
 * 访客身份关联仓储。
 * 提供匿名 visitorId 与已验证 Waline 资料的查询和更新。
 * @author Wreckloud
 * @since 2026-06-09
 */
public interface AnalyticsVisitorIdentityRepository extends JpaRepository<AnalyticsVisitorIdentityEntity, Long> {

    Optional<AnalyticsVisitorIdentityEntity> findByVisitorId(String visitorId);

    List<AnalyticsVisitorIdentityEntity> findByVisitorIdIn(Collection<String> visitorIds);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            OffsetDateTime start,
            OffsetDateTime end
    );
}
