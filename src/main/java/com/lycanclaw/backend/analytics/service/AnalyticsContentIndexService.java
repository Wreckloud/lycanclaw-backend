package com.lycanclaw.backend.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章索引读取服务。
 * 用于从 VitePress 的 posts.json 读取标题和标签，供写作洞察做文章与 tag 聚合。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Service
public class AnalyticsContentIndexService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsContentIndexService.class);
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private final ObjectMapper objectMapper;
    private final RecommendationProperties recommendationProperties;
    private final AnalyticsPathPolicy pathPolicy;
    private final String knowledgeStatsJsonPath;
    private volatile CachedIndex cachedIndex;

    public AnalyticsContentIndexService(
            ObjectMapper objectMapper,
            RecommendationProperties recommendationProperties,
            AnalyticsPathPolicy pathPolicy,
            @Value("${lycan.analytics.knowledge-stats-json-path:"
                    + "D:/Portfolio/Website/LycanClaw/frontend/docs/public/knowledge-stats.json}")
            String knowledgeStatsJsonPath
    ) {
        this.objectMapper = objectMapper;
        this.recommendationProperties = recommendationProperties;
        this.pathPolicy = pathPolicy;
        this.knowledgeStatsJsonPath = knowledgeStatsJsonPath;
    }

    /**
     * 返回按 URL 索引的文章元信息，缓存短时间复用以避免后台刷新频繁读文件。
     */
    public Map<String, PostInfo> loadPostMap() {
        CachedIndex current = cachedIndex;
        if (current != null && System.currentTimeMillis() - current.loadedAtMillis() <= CACHE_TTL_MILLIS) {
            return current.postsByUrl();
        }
        synchronized (this) {
            CachedIndex latest = cachedIndex;
            if (latest != null && System.currentTimeMillis() - latest.loadedAtMillis() <= CACHE_TTL_MILLIS) {
                return latest.postsByUrl();
            }
            CachedIndex rebuilt = new CachedIndex(System.currentTimeMillis(), readPosts());
            cachedIndex = rebuilt;
            return rebuilt.postsByUrl();
        }
    }

    private Map<String, PostInfo> readPosts() {
        Map<String, PostInfo> result = new LinkedHashMap<>();
        readThoughtPosts(result);
        readKnowledgePosts(result);
        return result;
    }

    /**
     * 读取随想文章索引并合并到统一内容索引。
     */
    private void readThoughtPosts(Map<String, PostInfo> result) {
        Path path = Path.of(recommendationProperties.getPostsJsonPath());
        if (!Files.exists(path)) {
            log.warn("未找到随想文章索引: {}", path);
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            if (!root.isArray()) {
                log.warn("随想文章索引格式异常，根节点不是数组: {}", path);
                return;
            }
            for (JsonNode node : root) {
                PostInfo post = parseThoughtPost(node);
                if (post != null) {
                    result.put(post.url(), post);
                }
            }
        } catch (IOException ex) {
            log.warn("读取随想文章索引失败: {}", path, ex);
        }
    }

    /**
     * 读取知识笔记统计产物并合并到统一内容索引。
     */
    private void readKnowledgePosts(Map<String, PostInfo> result) {
        Path path = Path.of(knowledgeStatsJsonPath);
        if (!Files.exists(path)) {
            log.warn("未找到知识笔记索引: {}", path);
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            if (!root.isArray()) {
                log.warn("知识笔记索引格式异常，根节点不是数组: {}", path);
                return;
            }
            for (JsonNode node : root) {
                PostInfo post = parseKnowledgePost(node);
                if (post != null) {
                    result.put(post.url(), post);
                }
            }
        } catch (IOException ex) {
            log.warn("读取知识笔记索引失败: {}", path, ex);
        }
    }

    private PostInfo parseThoughtPost(JsonNode node) {
        JsonNode frontmatter = node.path("frontmatter");
        if (!frontmatter.path("publish").asBoolean(false)) {
            return null;
        }
        String url = pathPolicy.normalizePath(node.path("url").asText(""));
        if (!pathPolicy.isArticle(url)) {
            return null;
        }
        String title = frontmatter.path("title").asText(url).trim();
        return new PostInfo(url, title.isBlank() ? url : title, normalizeTags(frontmatter.path("tags")));
    }

    private PostInfo parseKnowledgePost(JsonNode node) {
        String relativePath = node.path("relativePath").asText("").trim().replace('\\', '/');
        if (!relativePath.startsWith("knowledge/") || !relativePath.endsWith(".md")) {
            return null;
        }
        String url = pathPolicy.normalizePath("/" + relativePath.substring(0, relativePath.length() - 3) + ".html");
        if (!pathPolicy.isArticle(url)) {
            return null;
        }
        String title = node.path("title").asText(url).trim();
        return new PostInfo(url, title.isBlank() ? url : title, normalizeTags(node.path("tags")));
    }

    private List<String> normalizeTags(JsonNode tagsNode) {
        if (tagsNode == null || !tagsNode.isArray()) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (JsonNode tagNode : tagsNode) {
            String tag = tagNode.asText("").trim();
            if (!tag.isBlank()) {
                tags.add(tag);
            }
        }
        return new ArrayList<>(tags);
    }

    public record PostInfo(String url, String title, List<String> tags) {
    }

    private record CachedIndex(long loadedAtMillis, Map<String, PostInfo> postsByUrl) {
    }
}
