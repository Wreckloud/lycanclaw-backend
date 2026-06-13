package com.lycanclaw.backend.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.analytics.dto.VisitorIdentityDto;
import com.lycanclaw.backend.analytics.dto.WalineIdentityLinkRequest;
import com.lycanclaw.backend.analytics.entity.AnalyticsVisitorIdentityEntity;
import com.lycanclaw.backend.analytics.repository.AnalyticsVisitorIdentityRepository;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 访客身份关联服务。
 * 验证 Waline token，并将安全的账号摘要绑定到匿名 visitorId。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Service
public class VisitorIdentityService {

    private static final DateTimeFormatter ANONYMOUS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    private final AnalyticsVisitorIdentityRepository repository;
    private final WalineGatewayClient walineGatewayClient;
    private final ZoneId zoneId;

    public VisitorIdentityService(
            AnalyticsVisitorIdentityRepository repository,
            WalineGatewayClient walineGatewayClient,
            @Value("${lycan.system.zone-id:Asia/Shanghai}") String zoneId
    ) {
        this.repository = repository;
        this.walineGatewayClient = walineGatewayClient;
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * 验证并更新当前匿名访客对应的 Waline 身份。
     */
    public VisitorIdentityDto linkWaline(WalineIdentityLinkRequest request) {
        if (request == null || request.visitorId() == null || request.visitorId().isBlank()) {
            throw new IllegalArgumentException("visitorId 不能为空");
        }
        if (request.walineToken() == null || request.walineToken().isBlank()) {
            throw new IllegalArgumentException("walineToken 不能为空");
        }

        String visitorId = truncate(request.visitorId().trim(), 96);
        JsonNode profile = walineGatewayClient.fetchTokenProfile(request.walineToken().trim());
        String userId = firstText(profile, "objectId", "id");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("Waline 身份缺少用户 ID");
        }

        AnalyticsVisitorIdentityEntity entity = repository.findByVisitorId(visitorId)
                .orElseGet(AnalyticsVisitorIdentityEntity::new);
        OffsetDateTime now = OffsetDateTime.now(zoneId);
        entity.setVisitorId(visitorId);
        entity.setWalineUserId(truncate(userId, 128));
        entity.setNickname(truncate(firstText(profile, "nick", "display_name", "name"), 128));
        entity.setAvatar(truncate(firstText(profile, "avatar", "avatarUrl", "image"), 1000));
        entity.setProvider(truncate(firstText(profile, "provider", "platform"), 32));
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        if (entity.getAnonymousLabel() == null || entity.getAnonymousLabel().isBlank()) {
            entity.setAnonymousLabel(nextAnonymousLabel(now));
        }
        entity.setUpdatedAt(now);
        return toDto(repository.save(entity));
    }

    /**
     * 为未登录访客建立稳定的可读编号。
     */
    public synchronized void ensureAnonymousIdentity(String visitorId, OffsetDateTime firstSeenAt) {
        if (visitorId == null || visitorId.isBlank() || "anonymous".equalsIgnoreCase(visitorId)) {
            return;
        }
        String normalizedVisitorId = truncate(visitorId, 96);
        if (repository.findByVisitorId(normalizedVisitorId).isPresent()) {
            return;
        }
        OffsetDateTime createdAt = firstSeenAt == null ? OffsetDateTime.now(zoneId) : firstSeenAt;
        AnalyticsVisitorIdentityEntity entity = new AnalyticsVisitorIdentityEntity();
        entity.setVisitorId(normalizedVisitorId);
        entity.setWalineUserId(null);
        entity.setNickname("");
        entity.setAvatar("");
        entity.setProvider("");
        entity.setAnonymousLabel(nextAnonymousLabel(createdAt));
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(createdAt);
        repository.save(entity);
    }

    /**
     * 返回登录昵称或稳定匿名编号。
     */
    public String displayName(AnalyticsVisitorIdentityEntity identity) {
        if (identity == null) {
            return "匿名访客";
        }
        if (identity.getNickname() != null && !identity.getNickname().isBlank()) {
            return identity.getNickname();
        }
        if (identity.getAnonymousLabel() != null && !identity.getAnonymousLabel().isBlank()) {
            return identity.getAnonymousLabel();
        }
        return "匿名访客";
    }

    private String nextAnonymousLabel(OffsetDateTime createdAt) {
        OffsetDateTime start = createdAt.toLocalDate().atStartOfDay(zoneId).toOffsetDateTime();
        long sequence = repository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                start,
                start.plusDays(1)
        ) + 1;
        return "匿名" + ANONYMOUS_DATE_FORMAT.format(createdAt) + sequence;
    }

    private VisitorIdentityDto toDto(AnalyticsVisitorIdentityEntity entity) {
        return new VisitorIdentityDto(
                entity.getVisitorId(),
                entity.getWalineUserId(),
                entity.getNickname(),
                entity.getAvatar(),
                entity.getProvider(),
                entity.getUpdatedAt().toString()
        );
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String truncate(String value, int maxLength) {
        String resolved = value == null ? "" : value.trim();
        return resolved.length() <= maxLength ? resolved : resolved.substring(0, maxLength);
    }
}
