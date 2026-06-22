package com.lycanclaw.backend.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "recommendation_rules")
public class RecommendationRuleEntity {

    @Id
    @Column(name = "path", nullable = false, length = 255)
    private String path;

    @Column(name = "manual_rank")
    private Integer manualRank;

    @Column(name = "excluded", nullable = false)
    private boolean excluded;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getManualRank() {
        return manualRank;
    }

    public void setManualRank(Integer manualRank) {
        this.manualRank = manualRank;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
