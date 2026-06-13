package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.EncouragementSettleRequest;
import com.lycanclaw.backend.analytics.dto.EncouragementSettleResponse;
import com.lycanclaw.backend.analytics.entity.EncouragementEventEntity;
import com.lycanclaw.backend.analytics.repository.EncouragementEventRepository;
import com.lycanclaw.backend.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 首页催更结算服务。
 * 负责接收前端批量结算后的催更增量，后台总量统计只在管理端展示。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Service
public class EncouragementService {

    private static final int MAX_DELTA_PER_SETTLEMENT = 500;

    private final EncouragementEventRepository repository;
    private final ClientIpResolver clientIpResolver;
    private final VisitorIdentityService visitorIdentityService;
    private final ZoneId zoneId;

    public EncouragementService(
            EncouragementEventRepository repository,
            ClientIpResolver clientIpResolver,
            VisitorIdentityService visitorIdentityService,
            @Value("${lycan.system.zone-id:Asia/Shanghai}") String zoneId
    ) {
        this.repository = repository;
        this.clientIpResolver = clientIpResolver;
        this.visitorIdentityService = visitorIdentityService;
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * 保存一轮首页催更结算；path 固定归属首页整体，不绑定具体文章。
     */
    public EncouragementSettleResponse settle(EncouragementSettleRequest request, HttpServletRequest servletRequest) {
        int delta = normalizeDelta(request == null ? null : request.delta());
        EncouragementEventEntity entity = new EncouragementEventEntity();
        entity.setPath("/");
        entity.setTitle("首页催更");
        entity.setVisitorId(normalizeVisitorId(request == null ? "" : request.visitorId()));
        entity.setIp(clientIpResolver.resolve(servletRequest));
        entity.setUserAgent(truncate(servletRequest.getHeader("User-Agent"), 1000));
        entity.setDelta(delta);
        entity.setCreatedAt(OffsetDateTime.now(zoneId));
        repository.save(entity);
        visitorIdentityService.ensureAnonymousIdentity(entity.getVisitorId(), entity.getCreatedAt());
        return new EncouragementSettleResponse(true, delta);
    }

    private int normalizeDelta(Integer delta) {
        int resolved = delta == null ? 0 : delta;
        if (resolved <= 0) {
            throw new IllegalArgumentException("催更增量必须大于 0");
        }
        return Math.min(resolved, MAX_DELTA_PER_SETTLEMENT);
    }

    private String normalizeVisitorId(String value) {
        String resolved = value == null ? "" : value.trim();
        return truncate(resolved.isBlank() ? "anonymous" : resolved, 96);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
