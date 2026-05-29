package com.lycanclaw.backend.tag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.tag.config.TagProperties;
import com.lycanclaw.backend.tag.dto.ThoughtPostSummaryDto;
import com.lycanclaw.backend.tag.dto.ThoughtTagFilterResponseDto;
import com.lycanclaw.backend.tag.dto.ThoughtTagItemDto;
import com.lycanclaw.backend.tag.dto.ThoughtTagsResponseDto;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 提供Tag相关业务能力。
 *
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
     * 查询thought tags。
     */

    public ThoughtTagsResponseDto listThoughtTags() {
        List<ThoughtPostSummaryDto> posts = loadPublishedThoughts();
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (ThoughtPostSummaryDto post : posts) {
            for (String tag : post.tags()) {
                counter.merge(tag, 1, Integer::sum);
            }
        }

        List<ThoughtTagItemDto> tags = counter.entrySet().stream()
                .sorted(Comparator
                        .comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey, Comparator.naturalOrder()))
                .map(entry -> new ThoughtTagItemDto(entry.getKey(), entry.getValue()))
                .toList();

        return new ThoughtTagsResponseDto(tags, tags.size(), posts.size());
    }
    /**
     * 处理filter thoughts by tag业务逻辑。
     */

    public ThoughtTagFilterResponseDto filterThoughtsByTag(String tag, int page, int pageSize) {
        String normalizedTag = tag == null ? "" : tag.trim();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));

        List<ThoughtPostSummaryDto> source = loadPublishedThoughts();
        List<ThoughtPostSummaryDto> matched;
        if (normalizedTag.isBlank()) {
            matched = source;
        } else {
            matched = source.stream()
                    .filter(post -> post.tags().contains(normalizedTag))
                    .toList();
        }

        int total = matched.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) safePageSize);
        int offset = (safePage - 1) * safePageSize;

        List<ThoughtPostSummaryDto> pagedPosts;
        if (offset >= total) {
            pagedPosts = List.of();
        } else {
            int end = Math.min(offset + safePageSize, total);
            pagedPosts = matched.subList(offset, end);
        }

        return new ThoughtTagFilterResponseDto(
                normalizedTag,
                safePage,
                safePageSize,
                total,
                totalPages,
                pagedPosts
        );
    }

    /**
     * 手动刷新标签缓存并返回状态。
     */
    public synchronized Map<String, Object> refreshCache() {
        List<ThoughtPostSummaryDto> rebuilt = readPostsJson();
        long now = System.currentTimeMillis();
        cache = new CachedThoughts(now, rebuilt);
        return Map.of(
                "refreshedAtEpochMilli", now,
                "postCount", rebuilt.size(),
                "ttlSeconds", Math.max(5, properties.getCacheSeconds())
        );
    }

    /**
     * 查询当前标签缓存状态。
     */
    public Map<String, Object> cacheState() {
        CachedThoughts current = cache;
        if (current == null) {
            return Map.of(
                    "hasCache", false,
                    "expired", true,
                    "size", 0
            );
        }

        boolean expired = isExpired(current.loadedAtEpochMilli());
        return Map.of(
                "hasCache", true,
                "expired", expired,
                "size", current.posts().size(),
                "loadedAtEpochMilli", current.loadedAtEpochMilli(),
                "ttlSeconds", Math.max(5, properties.getCacheSeconds())
        );
    }

    private List<ThoughtPostSummaryDto> loadPublishedThoughts() {
        CachedThoughts current = cache;
        if (current != null && !isExpired(current.loadedAtEpochMilli)) {
            return current.posts;
        }

        synchronized (this) {
            CachedThoughts latest = cache;
            if (latest != null && !isExpired(latest.loadedAtEpochMilli)) {
                return latest.posts;
            }

            List<ThoughtPostSummaryDto> rebuilt = readPostsJson();
            cache = new CachedThoughts(System.currentTimeMillis(), rebuilt);
            return rebuilt;
        }
    }

    private boolean isExpired(long loadedAtEpochMilli) {
        long ttlMillis = Math.max(5, properties.getCacheSeconds()) * 1000L;
        return System.currentTimeMillis() - loadedAtEpochMilli > ttlMillis;
    }

    private List<ThoughtPostSummaryDto> readPostsJson() {
        Path postsPath = Path.of(properties.getPostsJsonPath());
        if (!Files.exists(postsPath)) {
            throw new IllegalStateException("未找到 posts.json: " + postsPath);
        }

        try {
            JsonNode root = objectMapper.readTree(Files.readString(postsPath));
            if (!root.isArray()) {
                return List.of();
            }

            List<ThoughtPostSummaryDto> posts = new ArrayList<>();
            for (JsonNode postNode : root) {
                ThoughtPostSummaryDto post = parsePost(postNode);
                if (post != null) {
                    posts.add(post);
                }
            }

            posts.sort(Comparator
                    .comparingLong((ThoughtPostSummaryDto item) -> parseDateEpoch(item.date())).reversed()
                    .thenComparing(ThoughtPostSummaryDto::title, String.CASE_INSENSITIVE_ORDER));
            return posts;
        } catch (IOException e) {
            throw new IllegalStateException("读取 posts.json 失败", e);
        }
    }

    private ThoughtPostSummaryDto parsePost(JsonNode postNode) {
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

        String description = frontmatter.path("description").asText("").trim();
        if (description.isEmpty()) {
            description = postNode.path("excerpt").asText("").trim();
        }

        String excerpt = postNode.path("excerpt").asText("").trim();
        List<String> tags = normalizeTags(frontmatter.path("tags"));

        int readMinutes = estimateReadMinutes(postNode.path("content").asText(""));
        return new ThoughtPostSummaryDto(url, title, description, date, tags, excerpt, readMinutes);
    }

    private boolean isThoughtPostUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (!url.startsWith("/thoughts/")) {
            return false;
        }
        return !url.endsWith("/index.html") && !url.endsWith("/tags.html");
    }

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

    private int estimateReadMinutes(String content) {
        if (content == null || content.isBlank()) {
            return 1;
        }

        // 与前端保持一致：按 300 词/分钟估算，至少 1 分钟。
        int words = countWords(content);
        return Math.max(1, (int) Math.ceil(words / 300.0));
    }

    private int countWords(String content) {
        int count = 0;
        String[] tokens = content.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4E00-\\u9FFF]+");
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }

            // CJK 按字符计数；其他按词计数。
            if (token.codePointAt(0) >= 0x4E00 && token.codePointAt(0) <= 0x9FFF) {
                count += token.length();
            } else {
                count += 1;
            }
        }
        return Math.max(count, 0);
    }

    private record CachedThoughts(long loadedAtEpochMilli, List<ThoughtPostSummaryDto> posts) {
    }
}
