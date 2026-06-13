package com.lycanclaw.backend.analytics.repository;

import com.lycanclaw.backend.analytics.entity.MusicListenSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 音乐收听会话仓储。
 * 提供会话幂等结算和后台收听统计查询。
 * @author Wreckloud
 * @since 2026-06-09
 */
public interface MusicListenSessionRepository extends JpaRepository<MusicListenSessionEntity, Long> {

    Optional<MusicListenSessionEntity> findByListenSessionId(String listenSessionId);

    List<MusicListenSessionEntity> findByStartedAtAfter(OffsetDateTime startedAt);
}
