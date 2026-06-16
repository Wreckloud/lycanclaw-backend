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
    private String postsJsonPath = "D:/Portfolio/Website/LycanClaw/frontend/docs/public/posts.json";

    /**
     * 手动推荐配置持久化路径（相对后端启动目录）。
     */
    private String manualConfigPath = "data/recommendation-manual.json";

    /**
     * 推荐聚合调度周期（毫秒），默认 5 分钟。
     */
    private long aggregationIntervalMillis = 300_000L;

    /**
     * 聚合任务每次抓取上游时的并发度。
     */
    private int aggregationFetchParallelism = 6;

    /**
     * 聚合任务每个候选抓取的超时时间（秒）。
     */
    private int aggregationFetchTimeoutSeconds = 15;

    /**
     * 聚合快照保鲜窗口（秒），超出后管理端会标记为过期。
     */
    private int snapshotFreshSeconds = 900;

    /**
     * 应用启动后是否自动异步触发首轮聚合。
     */
    private boolean startupWarmupEnabled = true;

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

    public long getAggregationIntervalMillis() {
        return aggregationIntervalMillis;
    }

    public void setAggregationIntervalMillis(long aggregationIntervalMillis) {
        this.aggregationIntervalMillis = aggregationIntervalMillis;
    }

    public int getAggregationFetchParallelism() {
        return aggregationFetchParallelism;
    }

    public void setAggregationFetchParallelism(int aggregationFetchParallelism) {
        this.aggregationFetchParallelism = aggregationFetchParallelism;
    }

    public int getAggregationFetchTimeoutSeconds() {
        return aggregationFetchTimeoutSeconds;
    }

    public void setAggregationFetchTimeoutSeconds(int aggregationFetchTimeoutSeconds) {
        this.aggregationFetchTimeoutSeconds = aggregationFetchTimeoutSeconds;
    }

    public int getSnapshotFreshSeconds() {
        return snapshotFreshSeconds;
    }

    public void setSnapshotFreshSeconds(int snapshotFreshSeconds) {
        this.snapshotFreshSeconds = snapshotFreshSeconds;
    }

    public boolean isStartupWarmupEnabled() {
        return startupWarmupEnabled;
    }

    public void setStartupWarmupEnabled(boolean startupWarmupEnabled) {
        this.startupWarmupEnabled = startupWarmupEnabled;
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
