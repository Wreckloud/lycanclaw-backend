package com.lycanclaw.backend.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.common.api.ErrorCode;
import com.lycanclaw.backend.common.json.JsonNodeExtractors;
import com.lycanclaw.backend.content.config.ContentProperties;
import com.lycanclaw.backend.music.service.NcmUpstreamClient;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.service.RecommendationService;
import com.lycanclaw.backend.stats.service.ArticleMetricSyncService;
import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.waline.config.WalineProperties;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维检查服务。
 * 执行依赖服务、索引文件与同步状态的运维健康检查。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class OpsCheckService {

    private final WalineGatewayClient walineGatewayClient;
    private final NcmUpstreamClient ncmUpstreamClient;
    private final JsonNodeExtractors jsonNodeExtractors;
    private final RecommendationService recommendationService;
    private final ArticleMetricSyncService articleMetricSyncService;
    private final ContentProperties contentProperties;
    private final WalineProperties walineProperties;
    private final AppTimeProvider appTimeProvider;

    public OpsCheckService(
            WalineGatewayClient walineGatewayClient,
            NcmUpstreamClient ncmUpstreamClient,
            JsonNodeExtractors jsonNodeExtractors,
            RecommendationService recommendationService,
            ArticleMetricSyncService articleMetricSyncService,
            ContentProperties contentProperties,
            WalineProperties walineProperties,
            AppTimeProvider appTimeProvider
    ) {
        this.walineGatewayClient = walineGatewayClient;
        this.ncmUpstreamClient = ncmUpstreamClient;
        this.jsonNodeExtractors = jsonNodeExtractors;
        this.recommendationService = recommendationService;
        this.articleMetricSyncService = articleMetricSyncService;
        this.contentProperties = contentProperties;
        this.walineProperties = walineProperties;
        this.appTimeProvider = appTimeProvider;
    }

    /**
     * 汇总管理端所需检查项：服务可用性、同步文件状态、常见错误提示。
     */
    public Map<String, Object> collectChecks() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", appTimeProvider.nowOffsetString());

        result.put("services", Map.of(
                "waline", checkWaline(),
                "ncmUpstream", checkNcmUpstream()
        ));

        result.put("sync", Map.of(
                "recommendationRules", recommendationRuleMeta(),
                "articleMetrics", articleMetricSyncService.snapshotState(),
                "postsJson", contentFileMeta(contentProperties.getPostsJsonPath()),
                "knowledgeStatsJson", contentFileMeta(contentProperties.getKnowledgeStatsJsonPath())
        ));

        result.put("commonErrors", List.of(
                Map.of(
                        "title", "Waline 401/403",
                        "cause", "SECURE_DOMAINS 不包含当前域名，或请求未走同域反代",
                        "suggestion", "检查 nginx 路由与 Waline SECURE_DOMAINS 配置"
                ),
                Map.of(
                        "title", "推荐列表为空",
                        "cause", "内容索引路径异常，或文章指标尚未成功同步",
                        "suggestion", "确认共享目录中的两个内容索引存在，然后手动同步文章指标"
                ),
                Map.of(
                        "title", "音乐接口失败",
                        "cause", "ncm-api 容器未启动或上游地址不通",
                        "suggestion", "检查 ncm-api 容器状态与 LYCAN_MUSIC_UPSTREAM_BASE_URL"
                )
        ));

        return result;
    }

    /**
     * 探测 Waline 评论与阅读统计链路可用性。
     */
    private Map<String, Object> checkWaline() {
        try {
            JsonNode recent = walineGatewayClient.fetchRecentComments(1);
            boolean validRecent = recent.isArray() || recent.path("data").isArray();
            if (!validRecent) {
                return Map.of(
                        "ok", false,
                        "baseUrl", walineProperties.getBaseUrl(),
                        "errorCode", ErrorCode.INTERNAL_ERROR.code(),
                        "error", "Waline recent comments response shape is invalid"
                );
            }
            int sampleRecent = recent.isArray() ? recent.size() : recent.path("data").size();
            int pageview = walineGatewayClient.fetchPageview("/");
            return Map.of(
                    "ok", true,
                    "baseUrl", walineProperties.getBaseUrl(),
                    "sampleRecentCount", Math.max(sampleRecent, 0),
                    "samplePageview", Math.max(pageview, 0)
            );
        } catch (Exception ex) {
            return failureWithBaseUrl(walineProperties.getBaseUrl(), ex);
        }
    }

    /**
     * 探测音乐上游服务可用性（使用登录状态接口做轻量探针）。
     */
    private Map<String, Object> checkNcmUpstream() {
        try {
            JsonNode status = ncmUpstreamClient.get("/login/status", Map.of());
            int code = jsonNodeExtractors.findInt(status, "code").orElse(-1);
            boolean anonymous = status.path("data").path("account").path("anonimousUser").asBoolean(false);
            boolean ok = code == 200 || code == 301;
            return Map.of(
                    "reachable", true,
                    "ok", ok,
                    "sampleStatusCode", code,
                    "loginState", anonymous ? "anonymous" : "account"
            );
        } catch (Exception ex) {
            return Map.of(
                    "reachable", false,
                    "ok", false,
                    "errorCode", ErrorCode.INTERNAL_ERROR.code(),
                    "error", errorMessage(ex)
            );
        }
    }

    /**
     * 读取手动推荐配置的元数据，不返回完整内容。
     */
    private Map<String, Object> recommendationRuleMeta() {
        try {
            RecommendationManualConfigDto dto = recommendationService.getManualConfig();
            return Map.of(
                    "ok", true,
                    "updatedAt", dto.updatedAt() == null ? "" : dto.updatedAt(),
                    "manualCount", dto.manualUrls() == null ? 0 : dto.manualUrls().size()
            );
        } catch (Exception ex) {
            return Map.of(
                    "ok", false,
                    "updatedAt", "",
                    "manualCount", 0,
                    "errorCode", ErrorCode.INTERNAL_ERROR.code(),
                    "error", errorMessage(ex)
            );
        }
    }

    /**
     * 返回索引文件存在性与体积，便于快速排查挂载或路径错误。
     */
    private Map<String, Object> contentFileMeta(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return Map.of(
                    "path", "",
                    "exists", false,
                    "size", 0L,
                    "errorCode", ErrorCode.BAD_REQUEST.code(),
                    "error", "path is blank"
            );
        }

        Path path = Path.of(pathValue);
        boolean exists = Files.exists(path);
        long size = 0L;
        try {
            if (exists) {
                size = Files.size(path);
            }
        } catch (Exception ex) {
            size = -1L;
            return Map.of(
                    "path", pathValue,
                    "exists", true,
                    "size", size,
                    "errorCode", ErrorCode.INTERNAL_ERROR.code(),
                    "error", errorMessage(ex)
            );
        }

        return Map.of(
                "path", pathValue,
                "exists", exists,
                "size", size
        );
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }

    private Map<String, Object> failureWithBaseUrl(String baseUrl, Exception ex) {
        return Map.of(
                "ok", false,
                "baseUrl", baseUrl,
                "errorCode", ErrorCode.INTERNAL_ERROR.code(),
                "error", errorMessage(ex)
        );
    }
}
