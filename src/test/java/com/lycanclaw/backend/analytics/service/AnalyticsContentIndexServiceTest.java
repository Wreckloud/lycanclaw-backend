package com.lycanclaw.backend.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内容索引读取服务测试。
 * 验证随想与知识笔记构建产物能够合并为统一的后台分析索引。
 * @author Wreckloud
 * @since 2026-06-09
 */
class AnalyticsContentIndexServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void mergesThoughtAndKnowledgeIndexesWithDecodedPaths() throws Exception {
        Path postsPath = tempDir.resolve("posts.json");
        Files.writeString(postsPath, """
                [
                  {
                    "url": "/thoughts/%E7%8B%BC.html",
                    "frontmatter": {
                      "publish": true,
                      "title": "狼",
                      "tags": ["随想"]
                    }
                  }
                ]
                """);
        Path knowledgePath = tempDir.resolve("knowledge-stats.json");
        Files.writeString(knowledgePath, """
                [
                  {
                    "title": "Java 基础",
                    "tags": ["Java", "后端"],
                    "relativePath": "knowledge/Java/Java 基础.md"
                  }
                ]
                """);

        RecommendationProperties properties = new RecommendationProperties();
        properties.setPostsJsonPath(postsPath.toString());
        AnalyticsContentIndexService service = new AnalyticsContentIndexService(
                new ObjectMapper(),
                properties,
                new AnalyticsPathPolicy(),
                knowledgePath.toString()
        );

        Map<String, AnalyticsContentIndexService.PostInfo> posts = service.loadPostMap();

        assertThat(posts).containsKeys("/thoughts/狼.html", "/knowledge/Java/Java 基础.html");
        assertThat(posts.get("/knowledge/Java/Java 基础.html").tags()).containsExactly("Java", "后端");
    }
}
