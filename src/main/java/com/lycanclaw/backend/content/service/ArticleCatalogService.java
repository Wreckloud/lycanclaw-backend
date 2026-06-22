package com.lycanclaw.backend.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.common.path.WebPathNormalizer;
import com.lycanclaw.backend.content.config.ContentProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ArticleCatalogService {

    private final ObjectMapper objectMapper;
    private final ContentProperties properties;
    private volatile CatalogSnapshot snapshot;

    public ArticleCatalogService(ObjectMapper objectMapper, ContentProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<ArticleCatalogItem> loadPublishedThoughts() {
        Path path = resolvePath();
        long lastModified = lastModified(path);
        CatalogSnapshot current = snapshot;
        if (current != null && current.lastModified() == lastModified) {
            return current.items();
        }

        synchronized (this) {
            CatalogSnapshot latest = snapshot;
            if (latest != null && latest.lastModified() == lastModified) {
                return latest.items();
            }
            CatalogSnapshot rebuilt = new CatalogSnapshot(lastModified, readItems(path));
            snapshot = rebuilt;
            return rebuilt.items();
        }
    }

    public synchronized List<ArticleCatalogItem> refresh() {
        Path path = resolvePath();
        CatalogSnapshot rebuilt = new CatalogSnapshot(lastModified(path), readItems(path));
        snapshot = rebuilt;
        return rebuilt.items();
    }

    private Path resolvePath() {
        Path path = Path.of(properties.getPostsJsonPath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("未找到 posts.json: " + path);
        }
        return path;
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            throw new IllegalStateException("读取 posts.json 修改时间失败", ex);
        }
    }

    private List<ArticleCatalogItem> readItems(Path path) {
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            if (!root.isArray()) {
                throw new IllegalStateException("posts.json 数据结构异常，根节点不是数组");
            }

            List<ArticleCatalogItem> items = new ArrayList<>();
            Iterator<JsonNode> iterator = root.elements();
            while (iterator.hasNext()) {
                ArticleCatalogItem item = parseItem(iterator.next());
                if (item != null) {
                    items.add(item);
                }
            }
            return List.copyOf(items);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 posts.json 失败", ex);
        }
    }

    private ArticleCatalogItem parseItem(JsonNode postNode) {
        JsonNode frontmatter = postNode.path("frontmatter");
        if (!frontmatter.path("publish").asBoolean(false)) {
            return null;
        }

        String rawPath = postNode.path("url").asText("").trim();
        if (rawPath.isEmpty()) {
            return null;
        }
        String path = WebPathNormalizer.normalize(rawPath);
        if (!isThoughtPostPath(path)) {
            return null;
        }

        String title = frontmatter.path("title").asText("").trim();
        String date = frontmatter.path("date").asText("").trim();
        if (title.isEmpty() || date.isEmpty()) {
            return null;
        }

        String description = frontmatter.path("description").asText(postNode.path("excerpt").asText(""));
        Set<String> tags = new LinkedHashSet<>();
        JsonNode tagsNode = frontmatter.path("tags");
        if (tagsNode.isArray()) {
            for (JsonNode tagNode : tagsNode) {
                String tag = tagNode.asText("").trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        return new ArticleCatalogItem(path, title, description, date, List.copyOf(tags));
    }

    private boolean isThoughtPostPath(String path) {
        return path.startsWith("/thoughts/")
                && !path.endsWith("/index.html")
                && !path.endsWith("/tags.html");
    }

    private record CatalogSnapshot(long lastModified, List<ArticleCatalogItem> items) {
    }

    public record ArticleCatalogItem(
            String path,
            String title,
            String description,
            String date,
            List<String> tags
    ) {
    }
}
