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
 * 访客身份关联实体。
 * 保存匿名 visitorId 与服务端验证后的 Waline 基础资料，不持久化登录 token。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Entity
@Table(
        name = "analytics_visitor_identity",
        indexes = {
                @Index(name = "idx_visitor_identity_visitor", columnList = "visitor_id", unique = true),
                @Index(name = "idx_visitor_identity_waline_user", columnList = "waline_user_id")
        }
)
public class AnalyticsVisitorIdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_id", nullable = false, length = 96, unique = true)
    private String visitorId;

    @Column(name = "waline_user_id", length = 128)
    private String walineUserId;

    @Column(name = "nickname", nullable = false, length = 128)
    private String nickname;

    @Column(name = "anonymous_label", length = 32, unique = true)
    private String anonymousLabel;

    @Column(name = "avatar", length = 1000)
    private String avatar;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public String getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(String visitorId) {
        this.visitorId = visitorId;
    }

    public String getWalineUserId() {
        return walineUserId;
    }

    public void setWalineUserId(String walineUserId) {
        this.walineUserId = walineUserId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAnonymousLabel() {
        return anonymousLabel;
    }

    public void setAnonymousLabel(String anonymousLabel) {
        this.anonymousLabel = anonymousLabel;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
