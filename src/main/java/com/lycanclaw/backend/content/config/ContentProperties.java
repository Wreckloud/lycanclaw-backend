package com.lycanclaw.backend.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内容索引配置。
 * 统一声明随想与知识笔记构建产物路径。
 * @author Wreckloud
 * @since 2026-06-23
 */
@ConfigurationProperties(prefix = "lycan.content")
public class ContentProperties {

    private String postsJsonPath = "../frontend/docs/public/posts.json";
    private String knowledgeStatsJsonPath = "../frontend/docs/public/knowledge-stats.json";

    public String getPostsJsonPath() {
        return postsJsonPath;
    }

    public void setPostsJsonPath(String postsJsonPath) {
        this.postsJsonPath = postsJsonPath;
    }

    public String getKnowledgeStatsJsonPath() {
        return knowledgeStatsJsonPath;
    }

    public void setKnowledgeStatsJsonPath(String knowledgeStatsJsonPath) {
        this.knowledgeStatsJsonPath = knowledgeStatsJsonPath;
    }
}
