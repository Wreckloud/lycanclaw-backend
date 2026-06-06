package com.lycanclaw.backend.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
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

    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private final ObjectMapper objectMapper;
    private final RecommendationProperties recommendationProperties;
    private final AnalyticsPathPolicy pathPolicy;
    private volatile CachedIndex cachedIndex;

    public AnalyticsContentIndexService(
            ObjectMapper objectMapper,
            RecommendationProperties recommendationProperties,
            AnalyticsPathPolicy pathPolicy
    ) {
        this.objectMapper = objectMapper;
        this.recommendationProperties = recommendationProperties;
        this.pathPolicy = pathPolicy;
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
        Path path = Path.of(recommendationProperties.getPostsJsonPath());
        if (!Files.exists(path)) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            if (!root.isArray()) {
                return Map.of();
            }
            Map<String, PostInfo> result = new LinkedHashMap<>();
            for (JsonNode node : root) {
                PostInfo post = parsePost(node);
                if (post != null) {
                    result.put(post.url(), post);
                }
            }
            return result;
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private PostInfo parsePost(JsonNode node) {
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
