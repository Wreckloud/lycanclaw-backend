package com.lycanclaw.backend.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.common.path.WebPathNormalizer;
import com.lycanclaw.backend.content.config.ContentProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 统一内容目录服务。
 * 合并已发布随想与知识笔记，供推荐、统计、评论和标签复用。
 * @author Wreckloud
 * @since 2026-06-23
 */
@Service
public class ContentCatalogService {

    private static final DateTimeFormatter CONTENT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);

    private final ObjectMapper objectMapper;
    private final ContentProperties properties;
    private volatile CatalogSnapshot snapshot;

    public ContentCatalogService(ObjectMapper objectMapper, ContentProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<ContentItem> loadPublishedArticles() {
        Path postsPath = requiredPath(properties.getPostsJsonPath(), "posts.json");
        Path knowledgePath = requiredPath(properties.getKnowledgeStatsJsonPath(), "knowledge-stats.json");
        long postsModifiedAt = lastModified(postsPath);
        long knowledgeModifiedAt = lastModified(knowledgePath);
        CatalogSnapshot current = snapshot;
        if (current != null && current.matches(postsModifiedAt, knowledgeModifiedAt)) {
            return current.items();
        }

        synchronized (this) {
            CatalogSnapshot latest = snapshot;
            if (latest != null && latest.matches(postsModifiedAt, knowledgeModifiedAt)) {
                return latest.items();
            }
            CatalogSnapshot rebuilt = buildSnapshot(postsPath, knowledgePath, postsModifiedAt, knowledgeModifiedAt);
            snapshot = rebuilt;
            return rebuilt.items();
        }
    }

    public List<ContentItem> loadPublishedThoughts() {
        return loadPublishedArticles().stream()
                .filter(item -> item.kind() == ContentKind.THOUGHT)
                .toList();
    }

    public Map<String, ContentItem> loadArticleMap() {
        Map<String, ContentItem> result = new LinkedHashMap<>();
        for (ContentItem item : loadPublishedArticles()) {
            result.put(item.path(), item);
        }
        return Map.copyOf(result);
    }

    public Optional<ContentItem> findPublishedArticle(String path) {
        return Optional.ofNullable(loadArticleMap().get(WebPathNormalizer.normalize(path)));
    }

    public ContentItem requirePublishedArticle(String path) {
        String normalized = WebPathNormalizer.normalize(path);
        return findPublishedArticle(normalized)
                .orElseThrow(() -> new IllegalArgumentException("文章不存在或未发布: " + normalized));
    }

    public synchronized List<ContentItem> refresh() {
        snapshot = null;
        return loadPublishedArticles();
    }

    private CatalogSnapshot buildSnapshot(
            Path postsPath,
            Path knowledgePath,
            long postsModifiedAt,
            long knowledgeModifiedAt
    ) {
        List<ContentItem> items = new ArrayList<>();
        items.addAll(readThoughts(postsPath));
        items.addAll(readKnowledge(knowledgePath));
        Set<String> paths = new LinkedHashSet<>();
        for (ContentItem item : items) {
            if (!paths.add(item.path())) {
                throw new IllegalStateException("内容索引存在重复路径: " + item.path());
            }
        }
        return new CatalogSnapshot(postsModifiedAt, knowledgeModifiedAt, List.copyOf(items));
    }

    private List<ContentItem> readThoughts(Path path) {
        JsonNode root = readArray(path, "posts.json");
        List<ContentItem> items = new ArrayList<>();
        for (JsonNode node : root) {
            JsonNode frontmatter = node.path("frontmatter");
            if (!frontmatter.path("publish").asBoolean(false)) {
                continue;
            }
            String articlePath = requiredText(node, "url", "posts.json");
            String normalizedPath = WebPathNormalizer.normalize(articlePath);
            if (!isThoughtPath(normalizedPath)) {
                throw new IllegalStateException("posts.json 包含非法随想路径: " + normalizedPath);
            }
            items.add(new ContentItem(
                    normalizedPath,
                    requiredText(frontmatter, "title", "posts.json"),
                    frontmatter.path("description").asText(node.path("excerpt").asText("")),
                    requiredDate(frontmatter, "posts.json"),
                    normalizeTags(frontmatter.path("tags")),
                    ContentKind.THOUGHT
            ));
        }
        return items;
    }

    private List<ContentItem> readKnowledge(Path path) {
        JsonNode root = readArray(path, "knowledge-stats.json");
        List<ContentItem> items = new ArrayList<>();
        for (JsonNode node : root) {
            String relativePath = requiredText(node, "relativePath", "knowledge-stats.json").replace('\\', '/');
            if (!relativePath.startsWith("knowledge/") || !relativePath.endsWith(".md")) {
                throw new IllegalStateException("knowledge-stats.json 包含非法知识路径: " + relativePath);
            }
            String articlePath = "/" + relativePath.substring(0, relativePath.length() - 3) + ".html";
            items.add(new ContentItem(
                    WebPathNormalizer.normalize(articlePath),
                    requiredText(node, "title", "knowledge-stats.json"),
                    "",
                    requiredDate(node, "knowledge-stats.json"),
                    normalizeTags(node.path("tags")),
                    ContentKind.KNOWLEDGE
            ));
        }
        return items;
    }

    private JsonNode readArray(Path path, String sourceName) {
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            if (!root.isArray()) {
                throw new IllegalStateException(sourceName + " 数据结构异常，根节点不是数组");
            }
            return root;
        } catch (IOException ex) {
            throw new IllegalStateException("读取 " + sourceName + " 失败", ex);
        }
    }

    private Path requiredPath(String pathValue, String sourceName) {
        if (pathValue == null || pathValue.isBlank()) {
            throw new IllegalStateException(sourceName + " 路径不能为空");
        }
        Path path = Path.of(pathValue);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("未找到 " + sourceName + ": " + path);
        }
        return path;
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            throw new IllegalStateException("读取内容索引修改时间失败: " + path, ex);
        }
    }

    private String requiredText(JsonNode node, String field, String sourceName) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException(sourceName + " 缺少字段: " + field);
        }
        return value;
    }

    private String requiredDate(JsonNode node, String sourceName) {
        String value = requiredText(node, "date", sourceName);
        try {
            LocalDateTime.parse(value, CONTENT_DATE_FORMATTER);
            return value;
        } catch (DateTimeParseException ex) {
            throw new IllegalStateException(sourceName + " 的 date 必须使用 yyyy-MM-dd HH:mm:ss", ex);
        }
    }

    private List<String> normalizeTags(JsonNode tagsNode) {
        if (!tagsNode.isArray()) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (JsonNode tagNode : tagsNode) {
            String tag = tagNode.asText("").trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return List.copyOf(tags);
    }

    private boolean isThoughtPath(String path) {
        return path.startsWith("/thoughts/")
                && path.endsWith(".html")
                && !path.endsWith("/index.html")
                && !path.endsWith("/tags.html");
    }

    public enum ContentKind {
        THOUGHT,
        KNOWLEDGE
    }

    public record ContentItem(
            String path,
            String title,
            String description,
            String date,
            List<String> tags,
            ContentKind kind
    ) {
    }

    private record CatalogSnapshot(
            long postsModifiedAt,
            long knowledgeModifiedAt,
            List<ContentItem> items
    ) {
        private boolean matches(long currentPostsModifiedAt, long currentKnowledgeModifiedAt) {
            return postsModifiedAt == currentPostsModifiedAt && knowledgeModifiedAt == currentKnowledgeModifiedAt;
        }
    }
}
