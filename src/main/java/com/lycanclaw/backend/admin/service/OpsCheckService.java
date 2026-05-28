package com.lycanclaw.backend.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.music.service.NcmUpstreamClient;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.service.RecommendationManualConfigService;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.tag.config.TagProperties;
import com.lycanclaw.backend.waline.config.WalineProperties;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维检查项聚合服务
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class OpsCheckService {

    private final WalineGatewayClient walineGatewayClient;
    private final NcmUpstreamClient ncmUpstreamClient;
    private final RecommendationManualConfigService recommendationManualConfigService;
    private final RecommendationProperties recommendationProperties;
    private final TagProperties tagProperties;
    private final WalineProperties walineProperties;

    public OpsCheckService(
            WalineGatewayClient walineGatewayClient,
            NcmUpstreamClient ncmUpstreamClient,
            RecommendationManualConfigService recommendationManualConfigService,
            RecommendationProperties recommendationProperties,
            TagProperties tagProperties,
            WalineProperties walineProperties
    ) {
        this.walineGatewayClient = walineGatewayClient;
        this.ncmUpstreamClient = ncmUpstreamClient;
        this.recommendationManualConfigService = recommendationManualConfigService;
        this.recommendationProperties = recommendationProperties;
        this.tagProperties = tagProperties;
        this.walineProperties = walineProperties;
    }

    public Map<String, Object> collectChecks() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());

        result.put("services", Map.of(
                "waline", checkWaline(),
                "ncmUpstream", checkNcmUpstream()
        ));

        result.put("sync", Map.of(
                "recommendationManual", recommendationManualMeta(),
                "postsJson", postsJsonMeta(recommendationProperties.getPostsJsonPath()),
                "tagPostsJson", postsJsonMeta(tagProperties.getPostsJsonPath())
        ));

        result.put("commonErrors", List.of(
                Map.of(
                        "title", "Waline 401/403",
                        "cause", "SECURE_DOMAINS 不包含当前域名，或请求未走同域反代",
                        "suggestion", "检查 nginx 路由与 Waline SECURE_DOMAINS 配置"
                ),
                Map.of(
                        "title", "推荐列表为空",
                        "cause", "posts.json 路径挂载错误或文章 publish 未开启",
                        "suggestion", "确认 POSTS_JSON_HOST_PATH 与 frontmatter.publish"
                ),
                Map.of(
                        "title", "音乐接口失败",
                        "cause", "ncm-api 容器未启动或上游地址不通",
                        "suggestion", "检查 ncm-api 容器状态与 LYCAN_MUSIC_UPSTREAM_BASE_URL"
                )
        ));

        return result;
    }

    private Map<String, Object> checkWaline() {
        try {
            JsonNode recent = walineGatewayClient.fetchRecentComments(1);
            int sampleRecent = recent.isArray() ? recent.size() : recent.path("data").size();
            int pageview = walineGatewayClient.fetchPageview("/");
            return Map.of(
                    "ok", true,
                    "baseUrl", walineProperties.getBaseUrl(),
                    "sampleRecentCount", Math.max(sampleRecent, 0),
                    "samplePageview", Math.max(pageview, 0)
            );
        } catch (Exception ex) {
            return Map.of(
                    "ok", false,
                    "baseUrl", walineProperties.getBaseUrl(),
                    "error", ex.getMessage()
            );
        }
    }

    private Map<String, Object> checkNcmUpstream() {
        try {
            JsonNode status = ncmUpstreamClient.get("/login/status", Map.of());
            return Map.of(
                    "ok", true,
                    "sampleStatusCode", status.path("code").asInt(-1)
            );
        } catch (Exception ex) {
            return Map.of(
                    "ok", false,
                    "error", ex.getMessage()
            );
        }
    }

    private Map<String, Object> recommendationManualMeta() {
        RecommendationManualConfigDto dto = recommendationManualConfigService.read();
        return Map.of(
                "updatedAt", dto.updatedAt() == null ? "" : dto.updatedAt(),
                "manualCount", dto.manualUrls().size()
        );
    }

    private Map<String, Object> postsJsonMeta(String pathValue) {
        Path path = Path.of(pathValue);
        boolean exists = Files.exists(path);
        long size = 0L;
        try {
            if (exists) {
                size = Files.size(path);
            }
        } catch (Exception ignored) {
            size = -1L;
        }

        return Map.of(
                "path", pathValue,
                "exists", exists,
                "size", size
        );
    }
}
