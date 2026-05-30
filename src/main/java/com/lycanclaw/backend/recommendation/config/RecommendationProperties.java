package com.lycanclaw.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 推荐配置属性。
 * 用于承载推荐相关外部配置项。
 * @author Wreckloud
 * @since 2026-05-15
 */
@ConfigurationProperties(prefix = "lycan.recommendation")
public class RecommendationProperties {

    /**
     * 前端构建输出的文章索引路径（posts.json）。
     */
    private String postsJsonPath = "D:/Portfolio/Website/LycanClaw/docs/public/posts.json";

    /**
     * 手动推荐配置持久化路径（相对后端启动目录）。
     */
    private String manualConfigPath = "data/recommendation-manual.json";

    /**
     * 热门结果缓存时间（秒）。
     */
    private int cacheSeconds = 300;

    /**
     * 参与热门计算的最多文章数。
     */
    private int maxCandidatePosts = 200;

    private Score score = new Score();

    public String getPostsJsonPath() {
        return postsJsonPath;
    }

    public void setPostsJsonPath(String postsJsonPath) {
        this.postsJsonPath = postsJsonPath;
    }

    public String getManualConfigPath() {
        return manualConfigPath;
    }

    public void setManualConfigPath(String manualConfigPath) {
        this.manualConfigPath = manualConfigPath;
    }

    public int getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(int cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }

    public int getMaxCandidatePosts() {
        return maxCandidatePosts;
    }

    public void setMaxCandidatePosts(int maxCandidatePosts) {
        this.maxCandidatePosts = maxCandidatePosts;
    }

    public Score getScore() {
        return score;
    }

    public void setScore(Score score) {
        this.score = score;
    }

    public static class Score {

        /**
         * 浏览量权重。
         */
        private double pageviewWeight = 1.0;

        /**
         * 评论数权重。
         */
        private double commentWeight = 24.0;

        public double getPageviewWeight() {
            return pageviewWeight;
        }

        public void setPageviewWeight(double pageviewWeight) {
            this.pageviewWeight = pageviewWeight;
        }

        public double getCommentWeight() {
            return commentWeight;
        }

        public void setCommentWeight(double commentWeight) {
            this.commentWeight = commentWeight;
        }
    }
}
