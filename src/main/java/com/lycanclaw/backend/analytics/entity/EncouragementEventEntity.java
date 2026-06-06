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
 * 催更结算事件实体。
 * 用于记录首页催更组件一次前端批量结算后的增量，不改变前台个人点击特效。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Entity
@Table(
        name = "encouragement_event",
        indexes = {
                @Index(name = "idx_encouragement_created_at", columnList = "created_at"),
                @Index(name = "idx_encouragement_visitor", columnList = "visitor_id"),
                @Index(name = "idx_encouragement_path", columnList = "path")
        }
)
public class EncouragementEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "path", nullable = false, length = 512)
    private String path;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "visitor_id", nullable = false, length = 96)
    private String visitorId;

    @Column(name = "ip", nullable = false, length = 64)
    private String ip;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "delta", nullable = false)
    private int delta;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
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

    public int getDelta() {
        return delta;
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
