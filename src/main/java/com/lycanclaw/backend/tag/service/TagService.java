package com.lycanclaw.backend.tag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.tag.config.TagProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 标签索引观察服务。
 * 主要用于管理端读取 VitePress 构建产物 posts.json，统计 thoughts 文章与标签状态，并提供缓存刷新能力。前台标签筛选仍由静态博客侧完成。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class TagService {

    private final ObjectMapper objectMapper;
    private final TagProperties properties;
    private volatile CachedThoughts cache;

    public TagService(ObjectMapper objectMapper, TagProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 返回管理端需要的标签索引摘要（thoughts 数量与标签总数）。
     */
    public Map<String, Object> summary() {
        CachedThoughts current = loadCachedThoughts();
        return Map.of(
                "thoughtPostCount", current.posts().size(),
                "tagCount", current.tagCount()
        );
    }

    /**
     * 手动刷新标签缓存并返回最新状态。
     */
    public synchronized Map<String, Object> refreshCache() {
        CachedThoughts rebuilt = buildCacheSnapshot();
        cache = rebuilt;
        return Map.of(
                "refreshedAtEpochMilli", rebuilt.loadedAtEpochMilli(),
                "thoughtPostCount", rebuilt.posts().size(),
                "tagCount", rebuilt.tagCount(),
                "ttlSeconds", Math.max(5, properties.getCacheSeconds())
        );
    }

    /**
     * 查询当前缓存状态，供管理端健康检查展示。
     */
    public Map<String, Object> cacheState() {
        CachedThoughts current = cache;
        if (current == null) {
            return Map.of(
                    "hasCache", false,
                    "expired", true,
                    "thoughtPostCount", 0,
                    "tagCount", 0
            );
        }

        boolean expired = isExpired(current.loadedAtEpochMilli());
        return Map.of(
                "hasCache", true,
                "expired", expired,
                "thoughtPostCount", current.posts().size(),
                "tagCount", current.tagCount(),
                "loadedAtEpochMilli", current.loadedAtEpochMilli(),
                "ttlSeconds", Math.max(5, properties.getCacheSeconds())
        );
    }

    /**
     * 加载缓存快照；过期或未命中时自动重建。
     */
    private CachedThoughts loadCachedThoughts() {
        CachedThoughts current = cache;
        if (current != null && !isExpired(current.loadedAtEpochMilli())) {
            return current;
        }

        synchronized (this) {
            CachedThoughts latest = cache;
            if (latest != null && !isExpired(latest.loadedAtEpochMilli())) {
                return latest;
            }
            CachedThoughts rebuilt = buildCacheSnapshot();
            cache = rebuilt;
            return rebuilt;
        }
    }

    /**
     * 读取 posts.json 并重建缓存快照。
     */
    private CachedThoughts buildCacheSnapshot() {
        List<ThoughtPostSummary> posts = readPostsJson();
        Set<String> tags = new LinkedHashSet<>();
        for (ThoughtPostSummary post : posts) {
            tags.addAll(post.tags());
        }
        return new CachedThoughts(System.currentTimeMillis(), posts, tags.size());
    }

    /**
     * 判断缓存是否过期。
     */
    private boolean isExpired(long loadedAtEpochMilli) {
        long ttlMillis = Math.max(5, properties.getCacheSeconds()) * 1000L;
        return System.currentTimeMillis() - loadedAtEpochMilli > ttlMillis;
    }

    /**
     * 读取构建产物 posts.json 并提取已发布 thoughts 文章。
     */
    private List<ThoughtPostSummary> readPostsJson() {
        Path postsPath = Path.of(properties.getPostsJsonPath());
        if (!Files.exists(postsPath)) {
            throw new IllegalStateException("未找到 posts.json: " + postsPath);
        }

        try {
            JsonNode root = objectMapper.readTree(Files.readString(postsPath));
            if (!root.isArray()) {
                throw new IllegalStateException("posts.json 数据结构异常，根节点不是数组");
            }

            List<ThoughtPostSummary> posts = new ArrayList<>();
            for (JsonNode postNode : root) {
                ThoughtPostSummary post = parsePost(postNode);
                if (post != null) {
                    posts.add(post);
                }
            }

            posts.sort(Comparator
                    .comparingLong((ThoughtPostSummary item) -> parseDateEpoch(item.date())).reversed()
                    .thenComparing(ThoughtPostSummary::title, String.CASE_INSENSITIVE_ORDER));
            return posts;
        } catch (IOException e) {
            throw new IllegalStateException("读取 posts.json 失败", e);
        }
    }

    /**
     * 从单篇文章节点中提取用于统计的 thoughts 摘要。
     */
    private ThoughtPostSummary parsePost(JsonNode postNode) {
        JsonNode frontmatter = postNode.path("frontmatter");
        if (!frontmatter.path("publish").asBoolean(false)) {
            return null;
        }

        String url = postNode.path("url").asText("").trim();
        if (!isThoughtPostUrl(url)) {
            return null;
        }

        String title = frontmatter.path("title").asText("").trim();
        String date = frontmatter.path("date").asText("").trim();
        if (title.isEmpty() || date.isEmpty()) {
            return null;
        }

        List<String> tags = normalizeTags(frontmatter.path("tags"));
        return new ThoughtPostSummary(url, title, date, tags);
    }

    /**
     * 仅保留 /thoughts/ 下的真实文章页，排除导航页面。
     */
    private boolean isThoughtPostUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (!url.startsWith("/thoughts/")) {
            return false;
        }
        return !url.endsWith("/index.html") && !url.endsWith("/tags.html");
    }

    /**
     * 规范化标签数组：去空值、去重、保留输入顺序。
     */
    private List<String> normalizeTags(JsonNode tagsNode) {
        if (tagsNode == null || !tagsNode.isArray()) {
            return List.of();
        }
        Set<String> uniqueTags = new LinkedHashSet<>();
        for (JsonNode tagNode : tagsNode) {
            String tag = tagNode.asText("").trim();
            if (!tag.isEmpty()) {
                uniqueTags.add(tag);
            }
        }
        return new ArrayList<>(uniqueTags);
    }

    /**
     * 解析多种日期格式，统一转为毫秒时间戳供排序使用。
     */
    private long parseDateEpoch(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // try next parser
        }
        try {
            return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // try next parser
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
            return LocalDateTime.parse(trimmed, formatter).toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // try next parser
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }

    private record ThoughtPostSummary(String url, String title, String date, List<String> tags) {
    }

    private record CachedThoughts(long loadedAtEpochMilli, List<ThoughtPostSummary> posts, int tagCount) {
    }
}
