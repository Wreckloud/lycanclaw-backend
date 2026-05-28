package com.lycanclaw.backend.admin.service;

import com.lycanclaw.backend.admin.dto.AdminDashboardSummaryDto;
import com.lycanclaw.backend.admin.dto.AdminGovernanceSummaryDto;
import com.lycanclaw.backend.admin.dto.AdminMusicStatusDto;
import com.lycanclaw.backend.admin.dto.AdminOpsSummaryDto;
import com.lycanclaw.backend.admin.dto.AdminRiskControlSummaryDto;
import com.lycanclaw.backend.comment.service.CommentService;
import com.lycanclaw.backend.common.model.HealthLevel;
import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.music.dto.MusicLoginStatusDto;
import com.lycanclaw.backend.music.service.MusicAuthService;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.service.RecommendationManualConfigService;
import com.lycanclaw.backend.tag.dto.ThoughtTagsResponseDto;
import com.lycanclaw.backend.tag.service.TagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardService.class);

    private final MusicAuthService musicAuthService;
    private final RecommendationManualConfigService recommendationManualConfigService;
    private final TagService tagService;
    private final CommentService commentService;
    private final OpsCheckService opsCheckService;
    private final AdminGovernanceService adminGovernanceService;
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
            AppTimeProvider appTimeProvider
    ) {
        this.musicAuthService = musicAuthService;
        this.recommendationManualConfigService = recommendationManualConfigService;
        this.tagService = tagService;
        this.commentService = commentService;
        this.opsCheckService = opsCheckService;
        this.adminGovernanceService = adminGovernanceService;
        this.appTimeProvider = appTimeProvider;
    }

    public AdminDashboardSummaryDto buildSummary() {
        Map<String, Object> checks = Map.of();
        String opsError = "";
        try {
            checks = opsCheckService.collectChecks();
        } catch (Exception ex) {
            opsError = errorMessage(ex);
        }

        Map<String, Object> syncStatus = safeSyncStatus(checks);

        return new AdminDashboardSummaryDto(
                appTimeProvider.nowOffsetString(),
                musicSection(),
                governanceSection(syncStatus),
                riskSection(),
                opsSection(checks, opsError)
        );
    }

    /**
     * 聚合音乐登录状态；异常时返回降级状态，避免首页整体失败。
     */
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

    /**
     * 聚合推荐/标签/评论治理摘要；异常时返回红色降级状态。
     */
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
                    parseHealthLevel(syncStatus.get("level")),
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
                    HealthLevel.RED,
                    appTimeProvider.nowOffsetString(),
                    actions()
            );
        }
    }

    /**
     * 风控配置属于本地静态信息，默认不做降级分支。
     */
    private AdminRiskControlSummaryDto riskSection() {
        return new AdminRiskControlSummaryDto(
                Math.max(1, authRateLimit),
                Math.max(1, publicMusicRateLimit),
                "静态 token + Waline 会话 token",
                List.of(
                        "管理端统一使用 X-Lycan-Admin-Token 鉴权（支持静态 token 与会话 token）",
                        "管理员请求启用分钟级限流，降低暴力探测风险",
                        "管理员请求会记录访问日志（IP / URI / 方法 / 结果）"
                )
        );
    }

    /**
     * 运维检查摘要直接透传检查快照，并携带模块错误信息。
     */
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

    /**
     * 同步状态计算失败时返回 red，保证管理首页可渲染。
     */
    private Map<String, Object> safeSyncStatus(Map<String, Object> checks) {
        try {
            return adminGovernanceService.syncStatus(checks);
        } catch (Exception ex) {
            log.warn("Failed to build governance sync status", ex);
            return Map.of(
                    "level", HealthLevel.RED,
                    "checkedAt", appTimeProvider.nowOffsetString()
            );
        }
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    /**
     * 兼容 level 既可能是枚举对象，也可能是字符串（green/Green/GREEN）。
     */
    private HealthLevel parseHealthLevel(Object value) {
        if (value instanceof HealthLevel level) {
            return level;
        }
        return HealthLevel.fromValue(value == null ? HealthLevel.YELLOW.value() : String.valueOf(value));
    }

    private Map<String, String> actions() {
        return Map.of(
                "rebuildRecommendations", "/api/admin/governance/recommendations/rebuild",
                "refreshTags", "/api/admin/governance/tags/refresh",
                "syncStatus", "/api/admin/governance/sync-status"
        );
    }
}
