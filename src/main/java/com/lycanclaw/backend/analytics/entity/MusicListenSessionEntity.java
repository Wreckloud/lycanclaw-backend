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
 * 音乐收听会话实体。
 * 记录单次歌曲播放的累计收听时长、来源和完成状态。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Entity
@Table(
        name = "music_listen_session",
        indexes = {
                @Index(name = "idx_music_listen_session_key", columnList = "listen_session_id", unique = true),
                @Index(name = "idx_music_listen_started_at", columnList = "started_at"),
                @Index(name = "idx_music_listen_visitor", columnList = "visitor_id"),
                @Index(name = "idx_music_listen_song", columnList = "song_id")
        }
)
public class MusicListenSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listen_session_id", nullable = false, length = 96, unique = true)
    private String listenSessionId;

    @Column(name = "visitor_id", nullable = false, length = 96)
    private String visitorId;

    @Column(name = "ip", nullable = false, length = 64)
    private String ip;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "song_id", nullable = false, length = 64)
    private String songId;

    @Column(name = "song_name", nullable = false, length = 255)
    private String songName;

    @Column(name = "artist", nullable = false, length = 255)
    private String artist;

    @Column(name = "playback_source", nullable = false, length = 64)
    private String playbackSource;

    @Column(name = "url_source", nullable = false, length = 32)
    private String urlSource;

    @Column(name = "page_path", nullable = false, length = 512)
    private String pagePath;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "listened_ms", nullable = false)
    private long listenedMs;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    public String getListenSessionId() {
        return listenSessionId;
    }

    public void setListenSessionId(String listenSessionId) {
        this.listenSessionId = listenSessionId;
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

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public String getSongName() {
        return songName;
    }

    public void setSongName(String songName) {
        this.songName = songName;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getPlaybackSource() {
        return playbackSource;
    }

    public void setPlaybackSource(String playbackSource) {
        this.playbackSource = playbackSource;
    }

    public String getUrlSource() {
        return urlSource;
    }

    public void setUrlSource(String urlSource) {
        this.urlSource = urlSource;
    }

    public String getPagePath() {
        return pagePath;
    }

    public void setPagePath(String pagePath) {
        this.pagePath = pagePath;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getListenedMs() {
        return listenedMs;
    }

    public void setListenedMs(long listenedMs) {
        this.listenedMs = listenedMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
