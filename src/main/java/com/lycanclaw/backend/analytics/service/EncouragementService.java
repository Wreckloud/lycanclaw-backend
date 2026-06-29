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
    private final ZoneId zoneId;

    public EncouragementService(
            EncouragementEventRepository repository,
            ClientIpResolver clientIpResolver,
            @Value("${lycan.system.zone-id:Asia/Shanghai}") String zoneId
    ) {
        this.repository = repository;
        this.clientIpResolver = clientIpResolver;
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * 保存一轮首页催更结算；path 固定归属首页整体，不绑定具体文章。
     */
    public EncouragementSettleResponse settle(EncouragementSettleRequest request, HttpServletRequest servletRequest) {
        int delta = normalizeDelta(request == null ? null : request.delta());
        EncouragementEventEntity entity = new EncouragementEventEntity();
        entity.setVisitorId(requireVisitorId(request == null ? null : request.visitorId()));
        entity.setIp(clientIpResolver.resolve(servletRequest));
        entity.setUserAgent(truncate(servletRequest.getHeader("User-Agent"), 1000));
        entity.setDelta(delta);
        entity.setCreatedAt(OffsetDateTime.now(zoneId));
        repository.save(entity);
        return new EncouragementSettleResponse(delta);
    }

    private int normalizeDelta(Integer delta) {
        int resolved = delta == null ? 0 : delta;
        if (resolved <= 0) {
            throw new IllegalArgumentException("催更增量必须大于 0");
        }
        if (resolved > MAX_DELTA_PER_SETTLEMENT) {
            throw new IllegalArgumentException("单次催更增量不能超过 " + MAX_DELTA_PER_SETTLEMENT);
        }
        return resolved;
    }

    private String requireVisitorId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("visitorId 不能为空");
        }
        String resolved = value.trim();
        if (resolved.length() > 96) {
            throw new IllegalArgumentException("visitorId 长度不能超过 96");
        }
        return resolved;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
