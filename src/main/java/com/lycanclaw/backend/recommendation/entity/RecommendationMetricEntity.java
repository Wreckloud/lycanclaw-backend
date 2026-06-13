package com.lycanclaw.backend.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 推荐聚合指标实体。
 * 保存文章热度快照，推荐接口直接读取该表避免请求链路实时拉取 Waline。
 * @author Wreckloud
 * @since 2026-05-31
 */
@Entity
@Table(
        name = "recommendation_metrics",
        indexes = {
                @Index(name = "idx_recommendation_metrics_updated_at", columnList = "updated_at"),
                @Index(name = "idx_recommendation_metrics_hot_score", columnList = "hot_score")
        }
)
public class RecommendationMetricEntity {

    @Id
    @Column(name = "url", nullable = false, length = 255)
    private String url;

    @Column(name = "pageview_count", nullable = false)
    private int pageviewCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "reaction_count", nullable = false)
    private int reactionCount;

    @Column(name = "hot_score", nullable = false)
    private double hotScore;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "source_status", nullable = false, length = 32)
    private String sourceStatus;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getPageviewCount() {
        return pageviewCount;
    }

    public void setPageviewCount(int pageviewCount) {
        this.pageviewCount = pageviewCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public int getReactionCount() {
        return reactionCount;
    }

    public void setReactionCount(int reactionCount) {
        this.reactionCount = reactionCount;
    }

    public double getHotScore() {
        return hotScore;
    }

    public void setHotScore(double hotScore) {
        this.hotScore = hotScore;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSourceStatus() {
        return sourceStatus;
    }

    public void setSourceStatus(String sourceStatus) {
        this.sourceStatus = sourceStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
