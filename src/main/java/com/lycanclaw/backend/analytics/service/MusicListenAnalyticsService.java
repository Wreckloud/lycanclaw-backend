package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.MusicListenSettleRequest;
import com.lycanclaw.backend.analytics.dto.MusicListenSettleResponse;
import com.lycanclaw.backend.analytics.entity.MusicListenSessionEntity;
import com.lycanclaw.backend.analytics.repository.MusicListenSessionRepository;
import com.lycanclaw.backend.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 音乐收听统计服务。
 * 按播放会话幂等结算累计收听时长，并保存歌曲、来源和访客信息。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Service
public class MusicListenAnalyticsService {

    private static final long MAX_LISTEN_MS = 12 * 60 * 60 * 1000L;

    private final MusicListenSessionRepository repository;
    private final ClientIpResolver clientIpResolver;
    private final AnalyticsPathPolicy pathPolicy;
    private final ZoneId zoneId;

    public MusicListenAnalyticsService(
            MusicListenSessionRepository repository,
            ClientIpResolver clientIpResolver,
            AnalyticsPathPolicy pathPolicy,
            @Value("${lycan.system.zone-id:Asia/Shanghai}") String zoneId
    ) {
        this.repository = repository;
        this.clientIpResolver = clientIpResolver;
        this.pathPolicy = pathPolicy;
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * 保存当前播放会话快照，重复上报时仅提升累计时长和完成状态。
     */
    @Transactional
    public MusicListenSettleResponse settle(
            MusicListenSettleRequest request,
            HttpServletRequest servletRequest
    ) {
        String sessionId = requireText(request == null ? null : request.listenSessionId(), "listenSessionId", 96);
        String visitorId = requireText(request.visitorId(), "visitorId", 96);
        String songId = requireText(request.songId(), "songId", 64);
        String pagePath = requireText(request.pagePath(), "pagePath", 512);
        if (!pathPolicy.isTrackable(pagePath)) {
            throw new IllegalArgumentException("pagePath 不是可统计的公开页面");
        }
        OffsetDateTime now = OffsetDateTime.now(zoneId);
        MusicListenSessionEntity entity = repository.findByListenSessionId(sessionId)
                .orElseGet(() -> createEntity(
                        sessionId,
                        visitorId,
                        songId,
                        pathPolicy.normalizePath(pagePath),
                        request,
                        servletRequest,
                        now
                ));

        if (!entity.getVisitorId().equals(visitorId) || !entity.getSongId().equals(songId)) {
            throw new IllegalArgumentException("listenSessionId 已被其他播放会话使用");
        }

        long durationMs = Math.max(entity.getDurationMs(), clamp(request.durationMs()));
        long listenedMs = Math.max(entity.getListenedMs(), clamp(request.listenedMs()));
        entity.setDurationMs(durationMs);
        entity.setListenedMs(durationMs > 0 ? Math.min(listenedMs, durationMs) : listenedMs);
        entity.setCompleted(entity.isCompleted() || (Boolean.TRUE.equals(request.completed()) && durationMs > 0));
        entity.setUpdatedAt(now);
        repository.save(entity);
        return new MusicListenSettleResponse(sessionId, entity.getListenedMs(), entity.isCompleted());
    }

    private MusicListenSessionEntity createEntity(
            String sessionId,
            String visitorId,
            String songId,
            String pagePath,
            MusicListenSettleRequest request,
            HttpServletRequest servletRequest,
            OffsetDateTime now
    ) {
        MusicListenSessionEntity entity = new MusicListenSessionEntity();
        entity.setListenSessionId(sessionId);
        entity.setVisitorId(visitorId);
        entity.setIp(clientIpResolver.resolve(servletRequest));
        entity.setUserAgent(truncate(servletRequest.getHeader("User-Agent"), 1000));
        entity.setSongId(songId);
        entity.setSongName(truncate(defaultValue(request.songName(), request.songId()), 255));
        entity.setArtist(truncate(defaultValue(request.artist(), "未知歌手"), 255));
        entity.setPlaybackSource(truncate(defaultValue(request.playbackSource(), "unknown"), 64));
        entity.setUrlSource(truncate(defaultValue(request.urlSource(), "unknown"), 32));
        entity.setPagePath(pagePath);
        entity.setStartedAt(now);
        entity.setUpdatedAt(now);
        entity.setListenedMs(0);
        entity.setDurationMs(0);
        entity.setCompleted(false);
        return entity;
    }

    private long clamp(Long value) {
        if (value == null || value < 0) {
            return 0;
        }
        return Math.min(value, MAX_LISTEN_MS);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String requireText(String value, String fieldName, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String truncate(String value, int maxLength) {
        String resolved = value == null ? "" : value.trim();
        return resolved.length() <= maxLength ? resolved : resolved.substring(0, maxLength);
    }
}
