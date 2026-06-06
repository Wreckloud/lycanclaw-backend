package com.lycanclaw.backend.analytics.repository;

import com.lycanclaw.backend.analytics.entity.EncouragementEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 催更事件仓储。
 * 提供催更增量保存、总量统计和最近事件查询。
 * @author Wreckloud
 * @since 2026-06-04
 */
public interface EncouragementEventRepository extends JpaRepository<EncouragementEventEntity, Long> {

    List<EncouragementEventEntity> findByCreatedAtAfter(OffsetDateTime createdAt);

    List<EncouragementEventEntity> findTop20ByOrderByCreatedAtDesc();

    @Query("select coalesce(sum(e.delta), 0) from EncouragementEventEntity e")
    long sumAllDelta();

    @Query("select coalesce(sum(e.delta), 0) from EncouragementEventEntity e where e.createdAt >= :createdAt")
    long sumDeltaAfter(@Param("createdAt") OffsetDateTime createdAt);
}
