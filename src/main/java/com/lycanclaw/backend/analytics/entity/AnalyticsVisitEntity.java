package com.lycanclaw.backend.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 页面访问统计实体。
 * 用于记录公开页面的一次访问，以及该访问最终结算出的有效停留时间。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Entity
@Table(
        name = "analytics_visit",
        indexes = {
                @Index(name = "idx_analytics_visit_visit_id", columnList = "visit_id", unique = true),
                @Index(name = "idx_analytics_visit_started_at", columnList = "started_at"),
                @Index(name = "idx_analytics_visit_path", columnList = "path"),
                @Index(name = "idx_analytics_visit_visitor", columnList = "visitor_id")
        }
)
public class AnalyticsVisitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visit_id", nullable = false, length = 64, unique = true)
    private String visitId;

    @Column(name = "path", nullable = false, length = 512)
    private String path;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "page_type", nullable = false, length = 32)
    private String pageType;

    @Column(name = "visitor_id", nullable = false, length = 96)
    private String visitorId;

    @Column(name = "ip", nullable = false, length = 64)
    private String ip;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "referrer", length = 1000)
    private String referrer;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "max_scroll_percent", nullable = false)
    private int maxScrollPercent;

    public Long getId() {
        return id;
    }

    public String getVisitId() {
        return visitId;
    }

    public void setVisitId(String visitId) {
        this.visitId = visitId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPageType() {
        return pageType;
    }

    public void setPageType(String pageType) {
        this.pageType = pageType;
    }

    public String getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(String visitorId) {
        this.visitorId = visitorId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getMaxScrollPercent() {
        return maxScrollPercent;
    }

    public void setMaxScrollPercent(int maxScrollPercent) {
        this.maxScrollPercent = maxScrollPercent;
    }
}
