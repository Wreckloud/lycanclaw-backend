package com.lycanclaw.backend.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.analytics.dto.VisitorIdentityDto;
import com.lycanclaw.backend.analytics.dto.WalineIdentityLinkRequest;
import com.lycanclaw.backend.analytics.entity.AnalyticsVisitorIdentityEntity;
import com.lycanclaw.backend.analytics.repository.AnalyticsVisitorIdentityRepository;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

/**
 * 访客身份关联服务。
 * 验证 Waline token，并将安全的账号摘要绑定到匿名 visitorId。
 * 匿名展示名由 visitorId 即时计算，身份表只保存已验证的 Waline 资料。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Service
public class VisitorIdentityService {

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
        entity.setUpdatedAt(now);
        return toDto(repository.save(entity));
    }

    /**
     * 返回登录昵称或由 visitorId 派生的稳定匿名编号。
     */
    public String displayName(String visitorId, AnalyticsVisitorIdentityEntity identity) {
        if (identity != null && identity.getNickname() != null && !identity.getNickname().isBlank()) {
            return identity.getNickname();
        }
        if (visitorId == null || visitorId.isBlank() || "anonymous".equalsIgnoreCase(visitorId)) {
            return "匿名访客";
        }
        return anonymousLabel(truncate(visitorId, 96));
    }

    private String anonymousLabel(String visitorId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(visitorId.getBytes(StandardCharsets.UTF_8));
            return "匿名-" + HexFormat.of().withUpperCase().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", ex);
        }
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
