package com.lycanclaw.backend.stats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文章指标同步配置。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
@ConfigurationProperties(prefix = "lycan.article-metrics")
public class ArticleMetricProperties {

    private long syncIntervalMillis = 600_000L;
    private int fetchParallelism = 6;
    private int fetchTimeoutSeconds = 15;
    private boolean startupSyncEnabled = true;

    public long getSyncIntervalMillis() {
        return syncIntervalMillis;
    }

    public void setSyncIntervalMillis(long syncIntervalMillis) {
        this.syncIntervalMillis = syncIntervalMillis;
    }

    public int getFetchParallelism() {
        return fetchParallelism;
    }

    public void setFetchParallelism(int fetchParallelism) {
        this.fetchParallelism = fetchParallelism;
    }

    public int getFetchTimeoutSeconds() {
        return fetchTimeoutSeconds;
    }

    public void setFetchTimeoutSeconds(int fetchTimeoutSeconds) {
        this.fetchTimeoutSeconds = fetchTimeoutSeconds;
    }

    public boolean isStartupSyncEnabled() {
        return startupSyncEnabled;
    }

    public void setStartupSyncEnabled(boolean startupSyncEnabled) {
        this.startupSyncEnabled = startupSyncEnabled;
    }
}
