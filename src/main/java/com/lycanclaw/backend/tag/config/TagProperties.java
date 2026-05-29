package com.lycanclaw.backend.tag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TagProperties：
 * 定义TagProperties相关配置。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@ConfigurationProperties(prefix = "lycan.tag")
public class TagProperties {

    /**
     * 前端构建输出的文章索引路径（posts.json）。
     */
    private String postsJsonPath = "D:/Portfolio/Website/LycanClaw/docs/public/posts.json";

    /**
     * 文章数据缓存秒数。
     */
    private int cacheSeconds = 300;

    public String getPostsJsonPath() {
        return postsJsonPath;
    }

    public void setPostsJsonPath(String postsJsonPath) {
        this.postsJsonPath = postsJsonPath;
    }

    public int getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(int cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }
}
