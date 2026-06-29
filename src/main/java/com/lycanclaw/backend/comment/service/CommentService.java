package com.lycanclaw.backend.comment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.comment.dto.RecentCommentDto;
import com.lycanclaw.backend.common.json.JsonNodeExtractors;
import com.lycanclaw.backend.analytics.service.AnalyticsPathPolicy;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import com.lycanclaw.backend.content.service.ContentCatalogService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论聚合服务。
 * 聚合 Waline 评论查询能力并提供最新评论摘要。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class CommentService {

    private static final int MAX_RECENT_COMMENT_LIMIT = 20;

    private final WalineGatewayClient walineGatewayClient;
    private final JsonNodeExtractors jsonNodeExtractors;
    private final CommentTextNormalizer commentTextNormalizer;
    private final ContentCatalogService contentCatalogService;
    private final AnalyticsPathPolicy pathPolicy;

    public CommentService(
            WalineGatewayClient walineGatewayClient,
            JsonNodeExtractors jsonNodeExtractors,
            CommentTextNormalizer commentTextNormalizer,
            ContentCatalogService contentCatalogService,
            AnalyticsPathPolicy pathPolicy
    ) {
        this.walineGatewayClient = walineGatewayClient;
        this.jsonNodeExtractors = jsonNodeExtractors;
        this.commentTextNormalizer = commentTextNormalizer;
        this.contentCatalogService = contentCatalogService;
        this.pathPolicy = pathPolicy;
    }

    /**
     * 查询最新评论摘要。
     */
    public List<RecentCommentDto> recentComments(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECENT_COMMENT_LIMIT));
        JsonNode node = walineGatewayClient.fetchRecentComments(safeLimit);
        List<RecentCommentDto> result = new ArrayList<>();

        if (node == null || node.isNull()) {
            return result;
        }

        JsonNode arrayNode = node.isArray() ? node : node.path("data");
        if (!arrayNode.isArray()) {
            return result;
        }

        for (JsonNode item : arrayNode) {
            result.add(parseRecentComment(item));
        }

        return result;
    }

    private RecentCommentDto parseRecentComment(JsonNode item) {
        String url = firstText(item, "url", "path");
        String path = firstText(item, "path", "url");
        String rawComment = firstText(item, "orig", "comment", "content");
        String normalizedPath = pathPolicy.normalizePath(path.isBlank() ? url : path);
        ContentCatalogService.ContentItem post = contentCatalogService.loadArticleMap().get(normalizedPath);
        return new RecentCommentDto(
                firstText(item, "objectId", "id"),
                firstText(item, "nick", "name"),
                commentTextNormalizer.toPlainText(rawComment),
                rawComment,
                firstText(item, "link", "website", "site"),
                url,
                normalizedPath,
                post == null ? fallbackTitle(normalizedPath) : post.title(),
                commentTime(item)
        );
    }

    private String commentTime(JsonNode item) {
        String timestamp = firstText(item, "createdAt", "insertedAt");
        if (!timestamp.isBlank()) {
            return timestamp;
        }
        long epochMillis = item.path("time").asLong(0);
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis).toString() : "";
    }

    private String fallbackTitle(String path) {
        int slash = path.lastIndexOf('/');
        String filename = slash >= 0 ? path.substring(slash + 1) : path;
        return filename.endsWith(".html") ? filename.substring(0, filename.length() - 5) : filename;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = jsonNodeExtractors.findText(node, field).orElse("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }
}
