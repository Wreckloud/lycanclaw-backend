package com.lycanclaw.backend.music.service;

import com.lycanclaw.backend.music.dto.MusicFlowStateDto;
import com.lycanclaw.backend.music.dto.MusicPlaybackItemDto;
import com.lycanclaw.backend.music.dto.MusicTrackDto;
import com.lycanclaw.backend.music.model.MusicFlowMode;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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
    private static final long SESSION_TTL_MS = 6L * 60 * 60 * 1000;
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{16,96}");

    private final MusicDataService musicDataService;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public MusicFlowService(MusicDataService musicDataService) {
        this.musicDataService = musicDataService;
    }

    /**
     * 启动随机流并返回当前状态。
     */
    public MusicFlowStateDto startRandom(String sessionId) {
        SessionState state = getOrCreateSession(sessionId);
        synchronized (state) {
            if (state.homeTrackIds.isEmpty()) {
                state.homeTrackIds = loadHomeTrackIds();
            }
            if (state.homeTrackIds.isEmpty()) {
                throw new IllegalStateException("随机歌单为空，无法启动随机流");
            }

            state.mode = MusicFlowMode.RANDOM;
            state.current = nextRandomItem(state);
            if (state.current == null) {
                throw new IllegalStateException("随机歌单中没有可播放歌曲");
            }
            state.aboutQueue.clear();
            state.lastAccessAt = System.currentTimeMillis();
            return toStateDto(state);
        }
    }

    /**
     * 启动关于页顺序流。
     * 无播放时按“点击项到第 5 首”播放；有播放时退化为打断插入单曲。
     */
    public MusicFlowStateDto startAboutSequence(String sessionId, String startSongId) {
        String safeSongId = normalizeSongId(startSongId, "startSongId");
        SessionState state = getOrCreateSession(sessionId);
        List<String> aboutTrackIds = loadAboutTrackIds();
        int startIndex = aboutTrackIds.indexOf(safeSongId);
        if (startIndex < 0) {
            throw new IllegalArgumentException("startSongId 不在关于页前五歌曲中");
        }

        synchronized (state) {
            if (state.current != null) {
                return applyInterrupt(state, safeSongId, "about-ranking");
            }

            state.aboutQueue.clear();
            for (int i = startIndex; i < aboutTrackIds.size(); i++) {
                state.aboutQueue.addLast(aboutTrackIds.get(i));
            }

            state.mode = MusicFlowMode.ABOUT_SEQUENCE;
            state.current = nextAboutItem(state);
            if (state.current == null) {
                throw new IllegalStateException("关于页榜单中没有可播放歌曲");
            }
            state.lastAccessAt = System.currentTimeMillis();
            return toStateDto(state);
        }
    }

    /**
     * 打断插入单曲，播放结束后回归随机流。
     */
    public MusicFlowStateDto interruptSingle(String sessionId, String songId, String source) {
        String safeSongId = normalizeSongId(songId, "songId");
        String safeSource = normalizeInterruptSource(source);

        SessionState state = getOrCreateSession(sessionId);
        synchronized (state) {
            if (state.homeTrackIds.isEmpty()) {
                state.homeTrackIds = loadHomeTrackIds();
            }
            return applyInterrupt(state, safeSongId, safeSource);
        }
    }

    /**
     * 推进到下一首。
     * 随机流持续播放；关于页流按顺序到末尾后暂停；打断单曲结束后切回随机流。
     */
    public MusicFlowStateDto next(String sessionId) {
        SessionState state = getOrCreateSession(sessionId);
        synchronized (state) {
            switch (state.mode) {
                case IDLE -> state.current = null;
                case RANDOM -> state.current = nextRandomItem(state);
                case ABOUT_SEQUENCE -> state.current = nextAboutItem(state);
                case INTERRUPT_SINGLE -> {
                    state.mode = MusicFlowMode.RANDOM;
                    state.current = nextRandomItem(state);
                }
            }

            state.lastAccessAt = System.currentTimeMillis();
            return toStateDto(state);
        }
    }

    /**
     * 停止并清空当前播放会话。
     */
    public MusicFlowStateDto stop(String sessionId) {
        sessions.remove(normalizeSessionId(sessionId));
        return new MusicFlowStateDto(MusicFlowMode.IDLE.value(), null);
    }

    private MusicPlaybackItemDto nextRandomItem(SessionState state) {
        if (state.homeTrackIds.isEmpty()) {
            state.homeTrackIds = loadHomeTrackIds();
        }
        if (state.homeTrackIds.isEmpty()) {
            state.mode = MusicFlowMode.IDLE;
            return null;
        }

        int remainingAttempts = state.homeTrackIds.size();
        String currentId = state.current == null ? "" : state.current.id();
        while (remainingAttempts-- > 0) {
            ensureRandomDeck(state, currentId);
            String nextId = state.randomDeck.pollFirst();
            if (nextId == null || nextId.isBlank()) {
                break;
            }
            MusicPlaybackItemDto item = buildPlaybackItem(nextId, "home-random");
            if (item != null) {
                return item;
            }
        }
        state.mode = MusicFlowMode.IDLE;
        return null;
    }

    private MusicPlaybackItemDto nextAboutItem(SessionState state) {
        while (!state.aboutQueue.isEmpty()) {
            String nextId = state.aboutQueue.pollFirst();
            if (nextId == null || nextId.isBlank()) {
                continue;
            }
            MusicPlaybackItemDto item = buildPlaybackItem(nextId, "about-ranking");
            if (item != null) {
                return item;
            }
        }
        state.mode = MusicFlowMode.IDLE;
        return null;
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

    private MusicPlaybackItemDto buildPlaybackItem(String songId, String source) {
        Map<String, Object> detail = musicDataService.getTrackDetailWithUrl(songId, null);
        String id = asText(detail.get("id"));
        String name = asText(detail.get("name"));
        String artist = asText(detail.get("artist"));
        String cover = asText(detail.get("cover"));
        String url = asText(detail.get("url"));
        String urlSource = asText(detail.get("source"));

        if (id.isBlank() || name.isBlank()) {
            throw new IllegalStateException("歌曲详情不完整，无法生成播放项");
        }
        if (url.isBlank()) {
            return null;
        }

        return new MusicPlaybackItemDto(
                id,
                name,
                artist,
                cover,
                url,
                source,
                urlSource
        );
    }

    private MusicFlowStateDto toStateDto(SessionState state) {
        return new MusicFlowStateDto(
                state.mode.value(),
                state.current
        );
    }

    private MusicFlowStateDto applyInterrupt(SessionState state, String songId, String source) {
        MusicPlaybackItemDto item = buildPlaybackItem(songId, source);
        if (item == null) {
            throw new IllegalStateException("当前歌曲暂时无法播放");
        }
        state.mode = MusicFlowMode.INTERRUPT_SINGLE;
        state.current = item;
        state.lastAccessAt = System.currentTimeMillis();
        return toStateDto(state);
    }

    private SessionState getOrCreateSession(String sessionId) {
        String safeSessionId = normalizeSessionId(sessionId);
        long now = System.currentTimeMillis();
        SessionState state = sessions.compute(safeSessionId, (key, current) ->
                current == null || current.expired(now) ? new SessionState(now) : current
        );
        state.lastAccessAt = now;
        cleanupExpiredSessions(now, safeSessionId);
        return state;
    }

    private String normalizeSessionId(String sessionId) {
        String normalized = sessionId == null ? "" : sessionId.trim();
        if (!SESSION_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("播放会话格式无效");
        }
        return normalized;
    }

    private String normalizeSongId(String songId, String fieldName) {
        if (songId == null || songId.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        String normalized = songId.trim();
        if (!normalized.matches("\\d{1,20}")) {
            throw new IllegalArgumentException(fieldName + " 格式无效");
        }
        return normalized;
    }

    private String normalizeInterruptSource(String source) {
        String normalized = source == null || source.isBlank() ? "article-embed" : source.trim();
        if (!"article-embed".equals(normalized) && !"about-ranking".equals(normalized)) {
            throw new IllegalArgumentException("不支持的单曲播放来源");
        }
        return normalized;
    }

    private void cleanupExpiredSessions(long now, String activeSessionId) {
        sessions.entrySet().removeIf(entry ->
                !entry.getKey().equals(activeSessionId) && entry.getValue().expired(now)
        );
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static class SessionState {
        private MusicFlowMode mode = MusicFlowMode.IDLE;
        private MusicPlaybackItemDto current;
        private List<String> homeTrackIds = new ArrayList<>();
        private Deque<String> randomDeck = new ArrayDeque<>();
        private Deque<String> aboutQueue = new ArrayDeque<>();
        private volatile long lastAccessAt;

        private SessionState(long lastAccessAt) {
            this.lastAccessAt = lastAccessAt;
        }

        private boolean expired(long now) {
            return now - lastAccessAt > SESSION_TTL_MS;
        }
    }
}
