package com.lycanclaw.backend.music.service;

import com.lycanclaw.backend.music.dto.MusicFlowStateDto;
import com.lycanclaw.backend.music.dto.MusicQueueItemDto;
import com.lycanclaw.backend.music.dto.MusicTrackDto;
import com.lycanclaw.backend.music.model.MusicFlowMode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音乐播放流服务。
 * 管理会话级播放模式（随机流、关于页顺序流、打断插入）并生成下一首。
 * @author Wreckloud
 * @since 2026-05-30
 */
@Service
public class MusicFlowService {

    private static final int HOME_POOL_SIZE = 50;
    private static final int ABOUT_POOL_SIZE = 5;
    private static final int PREVIEW_LIMIT = 3;
    private static final long SESSION_TTL_MS = 6L * 60 * 60 * 1000;

    private final MusicDataService musicDataService;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public MusicFlowService(MusicDataService musicDataService) {
        this.musicDataService = musicDataService;
    }

    /**
     * 启动随机流并返回当前状态。
     */
    public synchronized MusicFlowStateDto startRandom(String sessionId) {
        SessionState state = getOrCreateSession(sessionId);
        cleanupExpiredSessions();

        if (state.homeTrackIds.isEmpty()) {
            state.homeTrackIds = loadHomeTrackIds();
        }
        if (state.homeTrackIds.isEmpty()) {
            throw new IllegalStateException("随机歌单为空，无法启动随机流");
        }

        ensureRandomDeck(state, null);
        String nextId = state.randomDeck.pollFirst();
        if (nextId == null || nextId.isBlank()) {
            throw new IllegalStateException("随机流未生成有效歌曲");
        }

        state.mode = MusicFlowMode.RANDOM;
        state.current = buildQueueItem(nextId, "home-random");
        state.aboutQueue.clear();
        state.interruptSongId = "";
        state.lastAccessAt = System.currentTimeMillis();
        return toStateDto(state);
    }

    /**
     * 启动关于页顺序流。
     * 无播放时按“点击项到第 5 首”播放；有播放时退化为打断插入单曲。
     */
    public synchronized MusicFlowStateDto startAboutSequence(String sessionId, String startSongId) {
        String safeSongId = normalizeSongId(startSongId, "startSongId");
        SessionState state = getOrCreateSession(sessionId);
        cleanupExpiredSessions();

        List<String> aboutTrackIds = loadAboutTrackIds();
        int startIndex = aboutTrackIds.indexOf(safeSongId);
        if (startIndex < 0) {
            throw new IllegalArgumentException("startSongId 不在关于页前五歌曲中");
        }

        state.aboutQueue.clear();
        for (int i = startIndex; i < aboutTrackIds.size(); i++) {
            state.aboutQueue.addLast(aboutTrackIds.get(i));
        }

        if (state.current != null) {
            return interruptSingle(sessionId, safeSongId, "about-ranking");
        }

        String currentSongId = state.aboutQueue.pollFirst();
        if (currentSongId == null || currentSongId.isBlank()) {
            throw new IllegalStateException("关于页顺序流为空");
        }

        state.mode = MusicFlowMode.ABOUT_SEQUENCE;
        state.current = buildQueueItem(currentSongId, "about-ranking");
        state.interruptSongId = "";
        state.lastAccessAt = System.currentTimeMillis();
        return toStateDto(state);
    }

    /**
     * 打断插入单曲，播放结束后回归随机流。
     */
    public synchronized MusicFlowStateDto interruptSingle(String sessionId, String songId, String source) {
        String safeSongId = normalizeSongId(songId, "songId");
        String safeSource = source == null || source.isBlank() ? "article-embed" : source.trim();

        SessionState state = getOrCreateSession(sessionId);
        cleanupExpiredSessions();
        if (state.homeTrackIds.isEmpty()) {
            state.homeTrackIds = loadHomeTrackIds();
        }

        state.mode = MusicFlowMode.INTERRUPT_SINGLE;
        state.interruptSongId = safeSongId;
        state.current = buildQueueItem(safeSongId, safeSource);
        state.lastAccessAt = System.currentTimeMillis();
        return toStateDto(state);
    }

    /**
     * 推进到下一首。
     * 随机流持续播放；关于页流按顺序到末尾后暂停；打断单曲结束后切回随机流。
     */
    public synchronized MusicFlowStateDto next(String sessionId) {
        SessionState state = getOrCreateSession(sessionId);
        cleanupExpiredSessions();

        switch (state.mode) {
            case IDLE -> state.current = null;
            case RANDOM -> state.current = nextRandomItem(state);
            case ABOUT_SEQUENCE -> state.current = nextAboutItem(state);
            case INTERRUPT_SINGLE -> {
                state.interruptSongId = "";
                state.mode = MusicFlowMode.RANDOM;
                state.current = nextRandomItem(state);
            }
        }

        state.lastAccessAt = System.currentTimeMillis();
        return toStateDto(state);
    }

    /**
     * 返回当前流状态。
     */
    public synchronized MusicFlowStateDto state(String sessionId) {
        SessionState state = getOrCreateSession(sessionId);
        cleanupExpiredSessions();
        state.lastAccessAt = System.currentTimeMillis();
        return toStateDto(state);
    }

    /**
     * 停止并清空当前播放会话。
     */
    public synchronized MusicFlowStateDto stop(String sessionId) {
        SessionState state = getOrCreateSession(sessionId);
        cleanupExpiredSessions();

        state.mode = MusicFlowMode.IDLE;
        state.current = null;
        state.aboutQueue.clear();
        state.randomDeck.clear();
        state.interruptSongId = "";
        state.lastAccessAt = System.currentTimeMillis();
        return toStateDto(state);
    }

    private MusicQueueItemDto nextRandomItem(SessionState state) {
        if (state.homeTrackIds.isEmpty()) {
            state.homeTrackIds = loadHomeTrackIds();
        }
        if (state.homeTrackIds.isEmpty()) {
            state.mode = MusicFlowMode.IDLE;
            return null;
        }

        String currentId = state.current == null ? "" : state.current.id();
        ensureRandomDeck(state, currentId);
        String nextId = state.randomDeck.pollFirst();
        if (nextId == null || nextId.isBlank()) {
            state.mode = MusicFlowMode.IDLE;
            return null;
        }
        return buildQueueItem(nextId, "home-random");
    }

    private MusicQueueItemDto nextAboutItem(SessionState state) {
        String nextId = state.aboutQueue.pollFirst();
        if (nextId == null || nextId.isBlank()) {
            state.mode = MusicFlowMode.IDLE;
            return null;
        }
        return buildQueueItem(nextId, "about-ranking");
    }

    private void ensureRandomDeck(SessionState state, String currentSongId) {
        if (!state.randomDeck.isEmpty()) {
            return;
        }
        List<String> shuffled = new ArrayList<>(state.homeTrackIds);
        Collections.shuffle(shuffled);
        if (currentSongId != null && !currentSongId.isBlank() && shuffled.size() > 1) {
            shuffled.remove(currentSongId);
        }
        state.randomDeck = new ArrayDeque<>(shuffled);
    }

    private List<String> loadHomeTrackIds() {
        List<MusicTrackDto> tracks = musicDataService.getHomeTrackPool(HOME_POOL_SIZE);
        List<String> ids = new ArrayList<>(tracks.size());
        for (MusicTrackDto track : tracks) {
            if (track.id() == null || track.id().isBlank()) {
                continue;
            }
            ids.add(track.id());
        }
        return ids;
    }

    private List<String> loadAboutTrackIds() {
        List<MusicTrackDto> tracks = musicDataService.getAboutRankingTracks(ABOUT_POOL_SIZE);
        List<String> ids = new ArrayList<>(tracks.size());
        for (MusicTrackDto track : tracks) {
            if (track.id() == null || track.id().isBlank()) {
                continue;
            }
            ids.add(track.id());
        }
        return ids;
    }

    private MusicQueueItemDto buildQueueItem(String songId, String source) {
        Map<String, Object> detail = musicDataService.getTrackDetailWithUrl(songId, null);
        String id = asText(detail.get("id"));
        String name = asText(detail.get("name"));
        String artist = asText(detail.get("artist"));
        String cover = asText(detail.get("cover"));
        String url = asText(detail.get("url"));

        if (id.isBlank() || name.isBlank()) {
            throw new IllegalStateException("歌曲详情不完整，无法生成播放项");
        }

        return new MusicQueueItemDto(
                UUID.randomUUID().toString(),
                id,
                name,
                artist,
                cover,
                url,
                source,
                1,
                Instant.now().toString()
        );
    }

    private MusicFlowStateDto toStateDto(SessionState state) {
        List<MusicQueueItemDto> preview = new ArrayList<>();
        switch (state.mode) {
            case RANDOM -> {
                int count = 0;
                for (String id : state.randomDeck) {
                    preview.add(minimalPreviewItem(id, "home-random"));
                    count++;
                    if (count >= PREVIEW_LIMIT) break;
                }
            }
            case ABOUT_SEQUENCE -> {
                int count = 0;
                for (String id : state.aboutQueue) {
                    preview.add(minimalPreviewItem(id, "about-ranking"));
                    count++;
                    if (count >= PREVIEW_LIMIT) break;
                }
            }
            case INTERRUPT_SINGLE -> {
                if (!state.interruptSongId.isBlank()) {
                    preview.add(minimalPreviewItem(state.interruptSongId, "interrupt-single"));
                }
            }
            default -> {
            }
        }

        int queueSize = switch (state.mode) {
            case RANDOM -> state.randomDeck.size();
            case ABOUT_SEQUENCE -> state.aboutQueue.size();
            case INTERRUPT_SINGLE -> 1;
            default -> 0;
        };

        return new MusicFlowStateDto(
                state.mode.value(),
                state.current,
                queueSize,
                preview
        );
    }

    private MusicQueueItemDto minimalPreviewItem(String id, String source) {
        return new MusicQueueItemDto(
                "preview-" + id,
                id,
                "",
                "",
                "",
                "",
                source,
                1,
                ""
        );
    }

    private SessionState getOrCreateSession(String sessionId) {
        String safeSessionId = normalizeSessionId(sessionId);
        return sessions.computeIfAbsent(safeSessionId, ignored -> new SessionState());
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("播放会话不能为空");
        }
        return sessionId.trim();
    }

    private String normalizeSongId(String songId, String fieldName) {
        if (songId == null || songId.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return songId.trim();
    }

    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> now - entry.getValue().lastAccessAt > SESSION_TTL_MS);
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static class SessionState {
        private MusicFlowMode mode = MusicFlowMode.IDLE;
        private MusicQueueItemDto current;
        private List<String> homeTrackIds = new ArrayList<>();
        private Deque<String> randomDeck = new ArrayDeque<>();
        private Deque<String> aboutQueue = new ArrayDeque<>();
        private String interruptSongId = "";
        private long lastAccessAt = System.currentTimeMillis();
    }
}
