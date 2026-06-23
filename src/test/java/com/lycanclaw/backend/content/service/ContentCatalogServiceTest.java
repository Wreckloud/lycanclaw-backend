package com.lycanclaw.backend.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.content.config.ContentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 统一内容目录服务测试。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
class ContentCatalogServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void mergesPublishedThoughtsAndKnowledgeWithNormalizedPaths() throws Exception {
        ContentCatalogService service = serviceWith(
                """
                        [
                          {
                            "url": "/thoughts/%E7%8B%BC.html?from=test",
                            "excerpt": "摘要",
                            "frontmatter": {
                              "publish": true,
                              "title": "狼",
                              "date": "2026-06-20 10:00:00",
                              "tags": ["随想"]
                            }
                          },
                          {
                            "url": "/thoughts/draft.html",
                            "frontmatter": {"publish": false}
                          }
                        ]
                        """,
                """
                        [
                          {
                            "title": "Java 基础",
                            "date": "2026-06-21 10:00:00",
                            "tags": ["Java", "后端"],
                            "relativePath": "knowledge/Java/Java 基础.md"
                          }
                        ]
                        """
        );

        Map<String, ContentCatalogService.ContentItem> articles = service.loadArticleMap();

        assertThat(articles).containsOnlyKeys("/thoughts/狼.html", "/knowledge/Java/Java 基础.html");
        assertThat(service.loadPublishedThoughts()).extracting(ContentCatalogService.ContentItem::path)
                .containsExactly("/thoughts/狼.html");
        assertThat(service.findPublishedArticle("thoughts/%E7%8B%BC.html#title")).isPresent();
        assertThat(articles.get("/knowledge/Java/Java 基础.html").kind())
                .isEqualTo(ContentCatalogService.ContentKind.KNOWLEDGE);
    }

    @Test
    void failsWhenEitherRequiredIndexIsMissing() throws Exception {
        Path postsPath = tempDir.resolve("posts.json");
        Files.writeString(postsPath, "[]");
        ContentProperties properties = new ContentProperties();
        properties.setPostsJsonPath(postsPath.toString());
        properties.setKnowledgeStatsJsonPath(tempDir.resolve("missing.json").toString());

        ContentCatalogService service = new ContentCatalogService(new ObjectMapper(), properties);

        assertThatThrownBy(service::loadPublishedArticles)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("knowledge-stats.json");
    }

    @Test
    void failsWhenAnIndexHasInvalidStructure() throws Exception {
        ContentCatalogService service = serviceWith("{}", "[]");

        assertThatThrownBy(service::loadPublishedArticles)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("根节点不是数组");
    }

    @Test
    void rejectsNonStandardContentDate() throws Exception {
        ContentCatalogService service = serviceWith(
                """
                        [
                          {
                            "url": "/thoughts/example.html",
                            "frontmatter": {
                              "publish": true,
                              "title": "示例",
                              "date": "2026-06-20T10:00:00"
                            }
                          }
                        ]
                        """,
                "[]"
        );

        assertThatThrownBy(service::loadPublishedArticles)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yyyy-MM-dd HH:mm:ss");
    }

    private ContentCatalogService serviceWith(String postsJson, String knowledgeJson) throws Exception {
        Path postsPath = tempDir.resolve("posts.json");
        Path knowledgePath = tempDir.resolve("knowledge-stats.json");
        Files.writeString(postsPath, postsJson);
        Files.writeString(knowledgePath, knowledgeJson);
        ContentProperties properties = new ContentProperties();
        properties.setPostsJsonPath(postsPath.toString());
        properties.setKnowledgeStatsJsonPath(knowledgePath.toString());
        return new ContentCatalogService(new ObjectMapper(), properties);
    }
}
