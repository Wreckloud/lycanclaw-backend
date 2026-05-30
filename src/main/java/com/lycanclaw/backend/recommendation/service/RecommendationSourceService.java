package com.lycanclaw.backend.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 推荐数据源服务。
 * 读取文章索引数据并提供推荐算法输入。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class RecommendationSourceService {

    private final ObjectMapper objectMapper;
    private final RecommendationProperties properties;

    public RecommendationSourceService(ObjectMapper objectMapper, RecommendationProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 从 posts.json 读取候选文章（仅返回已发布的 thoughts 文章）。
     */
    public List<RecommendationCandidate> loadCandidates() {
        return limitCandidates(readCandidates(), Math.max(1, properties.getMaxCandidatePosts()));
    }

    /**
     * 从 posts.json 读取完整候选池（不裁剪数量）。
     */
    public List<RecommendationCandidate> loadAllCandidates() {
        return readCandidates();
    }

    private List<RecommendationCandidate> readCandidates() {
        Path postsPath = Path.of(properties.getPostsJsonPath());
        if (!Files.exists(postsPath)) {
            throw new IllegalStateException("未找到 posts.json: " + postsPath);
        }

        try {
            JsonNode root = objectMapper.readTree(Files.readString(postsPath));
            if (!root.isArray()) {
                return Collections.emptyList();
            }

            List<RecommendationCandidate> result = new ArrayList<>();
            Iterator<JsonNode> iterator = root.elements();
            while (iterator.hasNext()) {
                JsonNode post = iterator.next();
                RecommendationCandidate candidate = parseCandidate(post);
                if (candidate != null) {
                    result.add(candidate);
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("读取推荐候选文章失败", e);
        }
    }

    private List<RecommendationCandidate> limitCandidates(List<RecommendationCandidate> candidates, int max) {
        if (candidates.size() <= max) {
            return candidates;
        }
        return candidates.subList(0, max);
    }

    private RecommendationCandidate parseCandidate(JsonNode postNode) {
        JsonNode frontmatter = postNode.path("frontmatter");
        if (!frontmatter.path("publish").asBoolean(false)) {
            return null;
        }

        String url = postNode.path("url").asText("").trim();
        if (url.isEmpty() || !url.startsWith("/thoughts/") || url.endsWith("/index.html") || url.endsWith("/tags.html")) {
            return null;
        }

        String title = frontmatter.path("title").asText("").trim();
        String date = frontmatter.path("date").asText("").trim();
        if (title.isEmpty() || date.isEmpty()) {
            return null;
        }

        String description = frontmatter.path("description").asText(postNode.path("excerpt").asText(""));
        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = frontmatter.path("tags");
        if (tagsNode.isArray()) {
            for (JsonNode tagNode : tagsNode) {
                String tag = tagNode.asText("").trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
        }

        return new RecommendationCandidate(url, title, description, date, tags);
    }

    /**
     * 推荐候选文章模型，仅用于服务内部拼装。
     */
    public record RecommendationCandidate(
            String url,
            String title,
            String description,
            String date,
            List<String> tags
    ) {
    }
}
