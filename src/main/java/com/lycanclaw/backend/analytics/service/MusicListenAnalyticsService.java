package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.MusicListenSettleRequest;
import com.lycanclaw.backend.analytics.dto.MusicListenSettleResponse;
import com.lycanclaw.backend.analytics.entity.MusicListenSessionEntity;
import com.lycanclaw.backend.analytics.repository.MusicListenSessionRepository;
import com.lycanclaw.backend.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    private final VisitorIdentityService visitorIdentityService;
    private final ZoneId zoneId;

    public MusicListenAnalyticsService(
            MusicListenSessionRepository repository,
            ClientIpResolver clientIpResolver,
            AnalyticsPathPolicy pathPolicy,
            VisitorIdentityService visitorIdentityService,
            @Value("${lycan.system.zone-id:Asia/Shanghai}") String zoneId
    ) {
        this.repository = repository;
        this.clientIpResolver = clientIpResolver;
        this.pathPolicy = pathPolicy;
        this.visitorIdentityService = visitorIdentityService;
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * 保存当前播放会话快照，重复上报时仅提升累计时长和完成状态。
     */
    public MusicListenSettleResponse settle(
            MusicListenSettleRequest request,
            HttpServletRequest servletRequest
    ) {
        validate(request);
        String sessionId = truncate(request.listenSessionId(), 96);
        OffsetDateTime now = OffsetDateTime.now(zoneId);
        MusicListenSessionEntity entity = repository.findByListenSessionId(sessionId)
                .orElseGet(() -> createEntity(sessionId, request, servletRequest, now));

        entity.setListenedMs(Math.max(entity.getListenedMs(), clamp(request.listenedMs())));
        entity.setDurationMs(Math.max(entity.getDurationMs(), clamp(request.durationMs())));
        entity.setCompleted(entity.isCompleted() || Boolean.TRUE.equals(request.completed()));
        entity.setUpdatedAt(now);
        repository.save(entity);
        visitorIdentityService.ensureAnonymousIdentity(entity.getVisitorId(), entity.getStartedAt());
        return new MusicListenSettleResponse(sessionId, entity.getListenedMs(), entity.isCompleted());
    }

    private MusicListenSessionEntity createEntity(
            String sessionId,
            MusicListenSettleRequest request,
            HttpServletRequest servletRequest,
            OffsetDateTime now
    ) {
        MusicListenSessionEntity entity = new MusicListenSessionEntity();
        entity.setListenSessionId(sessionId);
        entity.setVisitorId(truncate(defaultValue(request.visitorId(), "anonymous"), 96));
        entity.setIp(clientIpResolver.resolve(servletRequest));
        entity.setUserAgent(truncate(servletRequest.getHeader("User-Agent"), 1000));
        entity.setSongId(truncate(request.songId(), 64));
        entity.setSongName(truncate(defaultValue(request.songName(), request.songId()), 255));
        entity.setArtist(truncate(defaultValue(request.artist(), "未知歌手"), 255));
        entity.setPlaybackSource(truncate(defaultValue(request.playbackSource(), "unknown"), 64));
        entity.setUrlSource(truncate(defaultValue(request.urlSource(), "unknown"), 32));
        entity.setPagePath(pathPolicy.normalizePath(request.pagePath()));
        entity.setStartedAt(now);
        entity.setUpdatedAt(now);
        entity.setListenedMs(0);
        entity.setDurationMs(0);
        entity.setCompleted(false);
        return entity;
    }

    private void validate(MusicListenSettleRequest request) {
        if (request == null || request.listenSessionId() == null || request.listenSessionId().isBlank()) {
            throw new IllegalArgumentException("listenSessionId 不能为空");
        }
        if (request.songId() == null || request.songId().isBlank()) {
            throw new IllegalArgumentException("songId 不能为空");
        }
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

    private String truncate(String value, int maxLength) {
        String resolved = value == null ? "" : value.trim();
        return resolved.length() <= maxLength ? resolved : resolved.substring(0, maxLength);
    }
}
