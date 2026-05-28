package com.lycanclaw.backend.admin.service;

import com.lycanclaw.backend.comment.service.CommentService;
import com.lycanclaw.backend.music.service.MusicAuthService;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.service.RecommendationManualConfigService;
import com.lycanclaw.backend.tag.dto.ThoughtTagsResponseDto;
import com.lycanclaw.backend.tag.service.TagService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description 管理端首页聚合服务
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Service
public class AdminDashboardService {

    private final MusicAuthService musicAuthService;
    private final RecommendationManualConfigService recommendationManualConfigService;
    private final TagService tagService;
    private final CommentService commentService;
    private final OpsCheckService opsCheckService;
    private final AdminGovernanceService adminGovernanceService;
    private final AdminRiskControlService adminRiskControlService;

    @Value("${lycan.security.auth-rate-limit-per-minute}")
    private int authRateLimit;

    @Value("${lycan.security.music-rate-limit-per-minute}")
    private int publicMusicRateLimit;

    public AdminDashboardService(
            MusicAuthService musicAuthService,
            RecommendationManualConfigService recommendationManualConfigService,
            TagService tagService,
            CommentService commentService,
            OpsCheckService opsCheckService,
            AdminGovernanceService adminGovernanceService,
            AdminRiskControlService adminRiskControlService
    ) {
        this.musicAuthService = musicAuthService;
        this.recommendationManualConfigService = recommendationManualConfigService;
        this.tagService = tagService;
        this.commentService = commentService;
        this.opsCheckService = opsCheckService;
        this.adminGovernanceService = adminGovernanceService;
        this.adminRiskControlService = adminRiskControlService;
    }

    public Map<String, Object> buildSummary() {
        Map<String, Object> checks = opsCheckService.collectChecks();
        Map<String, Object> syncStatus = adminGovernanceService.syncStatus(checks);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        result.put("music", musicSection());
        result.put("governance", governanceSection(syncStatus));
        result.put("riskControl", riskSection());
        result.put("ops", opsSection(checks));
        return result;
    }

    private Map<String, Object> musicSection() {
        try {
            Map<String, Object> status = musicAuthService.loginStatus();
            return Map.of(
                    "ok", true,
                    "loggedIn", Boolean.TRUE.equals(status.get("已登录")),
                    "nickname", String.valueOf(status.getOrDefault("nickname", "")),
                    "message", String.valueOf(status.getOrDefault("message", ""))
            );
        } catch (Exception ex) {
            return Map.of(
                    "ok", false,
                    "loggedIn", false,
                    "message", ex.getMessage()
            );
        }
    }

    private Map<String, Object> governanceSection(Map<String, Object> syncStatus) {
        RecommendationManualConfigDto config = recommendationManualConfigService.read();
        ThoughtTagsResponseDto tags = tagService.listThoughtTags();
        List<Map<String, Object>> recent = commentService.recentComments(5);
        return Map.of(
                "manualRecommendationCount", config.manualUrls().size(),
                "manualRecommendationUpdatedAt", config.updatedAt() == null ? "" : config.updatedAt(),
                "thoughtTagCount", tags.totalTags(),
                "thoughtPostCount", tags.totalPosts(),
                "recentCommentCount", recent.size(),
                "syncLevel", syncStatus.getOrDefault("level", "yellow"),
                "syncCheckedAt", syncStatus.getOrDefault("checkedAt", ""),
                "actions", Map.of(
                        "rebuildRecommendations", "/api/admin/governance/recommendations/rebuild",
                        "refreshTags", "/api/admin/governance/tags/refresh",
                        "syncStatus", "/api/admin/governance/sync-status"
                )
        );
    }

    private Map<String, Object> riskSection() {
        return Map.of(
                "adminApiRateLimitPerMinute", Math.max(1, authRateLimit),
                "publicMusicRateLimitPerMinute", Math.max(1, publicMusicRateLimit),
                "ipWhitelist", adminRiskControlService.whitelistSummary(),
                "notes", List.of(
                        "管理端统一使用管理员令牌（X-Lycan-Admin-Token）鉴权",
                        "支持管理端 IP 白名单；不在名单内将被拒绝访问",
                        "建议生产环境仅开放 80/443，容器内部端口不暴露公网"
                )
        );
    }

    private Map<String, Object> opsSection(Map<String, Object> checks) {
        Object services = checks.getOrDefault("services", Map.of());
        Object sync = checks.getOrDefault("sync", Map.of());
        return Map.of(
                "services", services,
                "sync", sync
        );
    }
}
