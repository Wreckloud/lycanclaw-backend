package com.lycanclaw.backend.admin.service;

import com.lycanclaw.backend.admin.dto.AdminDashboardSummaryDto;
import com.lycanclaw.backend.admin.dto.AdminGovernanceSummaryDto;
import com.lycanclaw.backend.admin.dto.AdminMusicStatusDto;
import com.lycanclaw.backend.admin.dto.AdminOpsSummaryDto;
import com.lycanclaw.backend.admin.dto.AdminRiskControlSummaryDto;
import com.lycanclaw.backend.comment.service.CommentService;
import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.music.dto.MusicLoginStatusDto;
import com.lycanclaw.backend.music.service.MusicAuthService;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.service.RecommendationManualConfigService;
import com.lycanclaw.backend.tag.dto.ThoughtTagsResponseDto;
import com.lycanclaw.backend.tag.service.TagService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 管理端首页聚合服务
 *
 * @author Wreckloud
 * @since 2026-05-15
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
    private final AppTimeProvider appTimeProvider;

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
            AdminRiskControlService adminRiskControlService,
            AppTimeProvider appTimeProvider
    ) {
        this.musicAuthService = musicAuthService;
        this.recommendationManualConfigService = recommendationManualConfigService;
        this.tagService = tagService;
        this.commentService = commentService;
        this.opsCheckService = opsCheckService;
        this.adminGovernanceService = adminGovernanceService;
        this.adminRiskControlService = adminRiskControlService;
        this.appTimeProvider = appTimeProvider;
    }

    public AdminDashboardSummaryDto buildSummary() {
        Map<String, Object> checks = null;
        String opsError = "";
        try {
            checks = opsCheckService.collectChecks();
        } catch (Exception ex) {
            opsError = errorMessage(ex);
        }

        Map<String, Object> safeChecks = checks == null ? Map.of() : checks;
        Map<String, Object> syncStatus = safeSyncStatus(safeChecks);

        return new AdminDashboardSummaryDto(
                appTimeProvider.nowOffsetString(),
                musicSection(),
                governanceSection(syncStatus),
                riskSection(),
                opsSection(safeChecks, opsError)
        );
    }

    private AdminMusicStatusDto musicSection() {
        try {
            MusicLoginStatusDto status = musicAuthService.loginStatus();
            return new AdminMusicStatusDto(
                    true,
                    status.loggedIn(),
                    status.nickname(),
                    status.message()
            );
        } catch (Exception ex) {
            return new AdminMusicStatusDto(false, false, "", errorMessage(ex));
        }
    }

    private AdminGovernanceSummaryDto governanceSection(Map<String, Object> syncStatus) {
        try {
            RecommendationManualConfigDto config = recommendationManualConfigService.read();
            ThoughtTagsResponseDto tags = tagService.listThoughtTags();
            List<Map<String, Object>> recent = commentService.recentComments(5);
            return new AdminGovernanceSummaryDto(
                    true,
                    "",
                    config.manualUrls().size(),
                    config.updatedAt() == null ? "" : config.updatedAt(),
                    tags.totalTags(),
                    tags.totalPosts(),
                    recent.size(),
                    String.valueOf(syncStatus.getOrDefault("level", "yellow")),
                    String.valueOf(syncStatus.getOrDefault("checkedAt", "")),
                    actions()
            );
        } catch (Exception ex) {
            return new AdminGovernanceSummaryDto(
                    false,
                    errorMessage(ex),
                    0,
                    "",
                    0,
                    0,
                    0,
                    "red",
                    appTimeProvider.nowOffsetString(),
                    actions()
            );
        }
    }

    private AdminRiskControlSummaryDto riskSection() {
        return new AdminRiskControlSummaryDto(
                Math.max(1, authRateLimit),
                Math.max(1, publicMusicRateLimit),
                adminRiskControlService.whitelistSummary(),
                List.of(
                        "管理端统一使用管理员令牌（X-Lycan-Admin-Token）鉴权",
                        "支持管理端 IP 白名单；不在名单内将被拒绝访问",
                        "建议生产环境仅开放 80/443，容器内部端口不暴露公网"
                )
        );
    }

    private AdminOpsSummaryDto opsSection(Map<String, Object> checks, String opsError) {
        Object services = checks.getOrDefault("services", Map.of());
        Object sync = checks.getOrDefault("sync", Map.of());
        return new AdminOpsSummaryDto(
                opsError.isBlank(),
                opsError,
                String.valueOf(checks.getOrDefault("checkedAt", appTimeProvider.nowOffsetString())),
                services,
                sync
        );
    }

    private Map<String, Object> safeSyncStatus(Map<String, Object> checks) {
        try {
            return adminGovernanceService.syncStatus(checks);
        } catch (Exception ignored) {
            return Map.of(
                    "level", "red",
                    "checkedAt", appTimeProvider.nowOffsetString()
            );
        }
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private Map<String, String> actions() {
        return Map.of(
                "rebuildRecommendations", "/api/admin/governance/recommendations/rebuild",
                "refreshTags", "/api/admin/governance/tags/refresh",
                "syncStatus", "/api/admin/governance/sync-status"
        );
    }
}
