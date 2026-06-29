package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.VisitEndRequest;
import com.lycanclaw.backend.analytics.dto.VisitEndResponse;
import com.lycanclaw.backend.analytics.dto.VisitStartRequest;
import com.lycanclaw.backend.analytics.dto.VisitStartResponse;
import com.lycanclaw.backend.analytics.entity.AnalyticsVisitEntity;
import com.lycanclaw.backend.analytics.repository.AnalyticsVisitRepository;
import com.lycanclaw.backend.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 页面访问统计服务。
 * 负责创建公开页面访问记录、结算有效停留时间，并过滤管理端和静态资源路径。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Service
public class AnalyticsVisitService {

    private static final long MAX_DURATION_MS = 30 * 60 * 1000L;

    private final AnalyticsVisitRepository repository;
    private final AnalyticsPathPolicy pathPolicy;
    private final ClientIpResolver clientIpResolver;
    private final ZoneId zoneId;

    public AnalyticsVisitService(
            AnalyticsVisitRepository repository,
            AnalyticsPathPolicy pathPolicy,
            ClientIpResolver clientIpResolver,
            @Value("${lycan.system.zone-id:Asia/Shanghai}") String zoneId
    ) {
        this.repository = repository;
        this.pathPolicy = pathPolicy;
        this.clientIpResolver = clientIpResolver;
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * 创建一次前台访问记录；非公开页面路径会被拒绝，避免污染统计数据。
     */
    public VisitStartResponse start(VisitStartRequest request, HttpServletRequest servletRequest) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            throw new IllegalArgumentException("访问路径不能为空");
        }
        String path = pathPolicy.normalizePath(request.path());
        if (!pathPolicy.isTrackable(path)) {
            throw new IllegalArgumentException("当前路径不纳入访问统计: " + path);
        }

        AnalyticsVisitEntity entity = new AnalyticsVisitEntity();
        entity.setVisitId(UUID.randomUUID().toString());
        entity.setPath(path);
        entity.setTitle(normalizeTitle(request == null ? "" : request.title(), path));
        entity.setPageType(pathPolicy.inferPageType(path));
        entity.setVisitorId(normalizeVisitorId(request.visitorId()));
        entity.setIp(clientIpResolver.resolve(servletRequest));
        entity.setUserAgent(truncate(servletRequest.getHeader("User-Agent"), 1000));
        entity.setReferrer(truncate(request.referrer(), 1000));
        entity.setStartedAt(now());
        entity.setDurationMs(0);
        entity.setMaxScrollPercent(0);
        repository.save(entity);
        return new VisitStartResponse(entity.getVisitId());
    }

    /**
     * 结算一次访问的有效停留时长；重复上报时保留更大的有效时长。
     */
    public VisitEndResponse end(VisitEndRequest request) {
        if (request == null || request.visitId() == null || request.visitId().isBlank()) {
            throw new IllegalArgumentException("visitId 不能为空");
        }
        long durationMs = clampDuration(request.durationMs());
        AnalyticsVisitEntity entity = repository.findByVisitId(request.visitId().trim())
                .orElseThrow(() -> new IllegalArgumentException("访问记录不存在: " + request.visitId()));
        entity.setDurationMs(Math.max(entity.getDurationMs(), durationMs));
        entity.setMaxScrollPercent(Math.max(
                entity.getMaxScrollPercent(),
                clampPercent(request.maxScrollPercent())
        ));
        entity.setEndedAt(now());
        repository.save(entity);
        return new VisitEndResponse(entity.getVisitId(), entity.getDurationMs());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(zoneId);
    }

    private long clampDuration(Long value) {
        if (value == null || value < 0) {
            return 0L;
        }
        return Math.min(value, MAX_DURATION_MS);
    }

    private int clampPercent(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return Math.min(value, 100);
    }

    private String normalizeVisitorId(String value) {
        String resolved = value == null ? "" : value.trim();
        if (resolved.isBlank()) {
            return "anonymous";
        }
        return truncate(resolved, 96);
    }

    private String normalizeTitle(String value, String fallback) {
        String resolved = value == null ? "" : value.trim();
        return truncate(resolved.isBlank() ? fallback : resolved, 255);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
