package com.lycanclaw.backend.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.common.json.JsonNodeExtractors;
import com.lycanclaw.backend.common.security.InMemorySlidingWindowRateLimiter;
import com.lycanclaw.backend.music.dto.MusicLyricLineDto;
import com.lycanclaw.backend.music.dto.MusicTrackDto;
import com.lycanclaw.backend.music.dto.MusicTrackLyricDto;
import com.lycanclaw.backend.music.model.MusicQualityLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 音乐数据聚合服务。
 * 获取歌曲数据和播放 URL，决策 public/login 并处理缓存与限流。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class MusicDataService {

    private static final String LOGIN_URL_GLOBAL_RATE_LIMIT_KEY = "music:login-url:global";
    private static final long SONG_DETAIL_CACHE_TTL_MS = 12L * 60 * 60 * 1000;
    private static final long TRACK_URL_PUBLIC_CACHE_TTL_MS = 5L * 60 * 1000;
    private static final long TRACK_URL_LOGIN_CACHE_TTL_MS = 3L * 60 * 1000;
    private static final long TRACK_URL_FAILURE_CACHE_TTL_MS = 30L * 1000;
    private static final long LYRIC_CACHE_TTL_MS = 12L * 60 * 60 * 1000;
    private static final Pattern LRC_LINE_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\](.*)");

    @Value("${lycan.music.fallback-uid}")
    private String fallbackUid;

    @Value("${lycan.music.playlist-owner-uid:629126546}")
    private String playlistOwnerUid;

    @Value("${lycan.music.preferred-level:exhigh}")
    private String preferredLevel;

    @Value("${lycan.security.music-login-url-global-limit-per-minute:60}")
    private int loginUrlGlobalLimitPerMinute;

    private final NcmUpstreamClient upstreamClient;
    private final MusicAuthSessionService sessionService;
    private final JsonNodeExtractors jsonNodeExtractors;
    private final InMemorySlidingWindowRateLimiter rateLimiter;
    private final Map<String, CacheEntry<SongDetail>> songDetailCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<TrackUrlResolveResult>> resolvedTrackUrlCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<MusicTrackLyricDto>> lyricCache = new ConcurrentHashMap<>();

    public MusicDataService(
            NcmUpstreamClient upstreamClient,
            MusicAuthSessionService sessionService,
            JsonNodeExtractors jsonNodeExtractors,
            InMemorySlidingWindowRateLimiter rateLimiter
    ) {
        this.upstreamClient = upstreamClient;
        this.sessionService = sessionService;
        this.jsonNodeExtractors = jsonNodeExtractors;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 拉取周听歌榜：固定读取配置账号，和登录态解耦。
     */
    public Map<String, Object> getWeeklyRanking(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String uid = resolvePlaylistOwnerUid();
        List<MusicTrackDto> tracks = fetchUserRecordTracks(uid, safeLimit, false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("limit", safeLimit);
        data.put("uid", uid);
        data.put("tracks", tracks);
        data.put("source", "playlist-owner");
        return data;
    }

    /**
     * 查询首页随机池（先 weekData，不足再补 allData）。
     */
    public List<MusicTrackDto> getHomeTrackPool(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String uid = resolvePlaylistOwnerUid();

        List<MusicTrackDto> weekTracks = fetchUserRecordTracks(uid, safeLimit, false);
        if (weekTracks.size() >= safeLimit) {
            return weekTracks;
        }

        List<MusicTrackDto> allTracks = fetchUserRecordTracks(uid, safeLimit, true);
        LinkedHashMap<String, MusicTrackDto> merged = new LinkedHashMap<>();
        for (MusicTrackDto track : weekTracks) {
            merged.put(track.id(), track);
        }
        for (MusicTrackDto track : allTracks) {
            merged.putIfAbsent(track.id(), track);
            if (merged.size() >= safeLimit) {
                break;
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 查询关于页榜单前 N 首（按最近一周顺序）。
     */
    public List<MusicTrackDto> getAboutRankingTracks(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        return fetchUserRecordTracks(resolvePlaylistOwnerUid(), safeLimit, false);
    }

    /**
     * 获取歌曲播放地址：
     * 1) 先用公开模式尝试（不带 cookie）；
     * 2) 公开全部失败后，再尝试登录模式（带 cookie）。
     */
    public Map<String, Object> getTrackUrl(String id, String level) {
        String safeId = validateSongId(id);
        String preferred = normalizeLevel(level);
        boolean hasLoginCookie = sessionService.hasCookie();
        String cacheKey = safeId + "|" + preferred + "|" + (hasLoginCookie ? "auth" : "anon");

        cleanupCaches();
        TrackUrlResolveResult cached = getCachedTrackUrl(cacheKey);
        if (cached != null) {
            return toTrackUrlResponse(safeId, preferred, cached);
        }

        List<String> levels = buildLevelAttempts(preferred);

        for (String attemptLevel : levels) {
            TrackUrlFetchResult publicResult = fetchTrackUrlWithLevel(safeId, attemptLevel, false);
            if (!publicResult.url().isBlank()) {
                TrackUrlResolveResult resolved = TrackUrlResolveResult.success(publicResult.url(), attemptLevel, "public");
                cacheTrackUrl(cacheKey, resolved, TRACK_URL_PUBLIC_CACHE_TTL_MS);
                return toTrackUrlResponse(safeId, preferred, resolved);
            }
        }

        if (!hasLoginCookie) {
            TrackUrlResolveResult failed = TrackUrlResolveResult.failure(preferred, "public_only");
            cacheTrackUrl(cacheKey, failed, TRACK_URL_FAILURE_CACHE_TTL_MS);
            return toTrackUrlResponse(safeId, preferred, failed);
        }

        boolean loginRateLimited = false;
        for (String attemptLevel : levels) {
            TrackUrlFetchResult authResult = fetchTrackUrlWithLevel(safeId, attemptLevel, true);
            if (authResult.rateLimited()) {
                loginRateLimited = true;
                continue;
            }
            if (!authResult.url().isBlank()) {
                TrackUrlResolveResult resolved = TrackUrlResolveResult.success(authResult.url(), attemptLevel, "login");
                cacheTrackUrl(cacheKey, resolved, TRACK_URL_LOGIN_CACHE_TTL_MS);
                return toTrackUrlResponse(safeId, preferred, resolved);
            }
        }

        String failureSource = loginRateLimited ? "login_rate_limited" : "login_fallback_failed";
        TrackUrlResolveResult failed = TrackUrlResolveResult.failure(preferred, failureSource);
        cacheTrackUrl(cacheKey, failed, TRACK_URL_FAILURE_CACHE_TTL_MS);
        return toTrackUrlResponse(safeId, preferred, failed);
    }

    /**
     * 获取歌曲详情并拼接最终可播放 URL。
     */
    public Map<String, Object> getTrackDetailWithUrl(String id, String level) {
        String safeId = validateSongId(id);
        SongDetail detail = getSongDetail(safeId);
        Map<String, Object> urlInfo = getTrackUrl(safeId, level);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", detail.id());
        data.put("name", detail.name());
        data.put("artist", detail.artist());
        data.put("cover", detail.cover());
        data.put("url", urlInfo.get("url"));
        data.put("level", urlInfo.get("level"));
        data.put("source", urlInfo.get("source"));
        return data;
    }

    /**
     * 获取歌曲原文歌词时间轴。
     */
    public MusicTrackLyricDto getTrackLyric(String id) {
        String safeId = validateSongId(id);
        cleanupCaches();

        CacheEntry<MusicTrackLyricDto> cached = lyricCache.get(safeId);
        long now = System.currentTimeMillis();
        if (cached != null && !cached.expired(now)) {
            return cached.value();
        }

        JsonNode lyricNode = upstreamClient.get("/lyric", Map.of("id", safeId));
        JsonNode lrcNode = lyricNode.path("lrc");
        String lrcText = safeText(lrcNode, "lyric");
        MusicTrackLyricDto parsed = parseLyric(safeId, lrcText);
        lyricCache.put(safeId, new CacheEntry<>(parsed, now + LYRIC_CACHE_TTL_MS));
        return parsed;
    }

    private String resolvePlaylistOwnerUid() {
        if (playlistOwnerUid != null && !playlistOwnerUid.isBlank()) {
            return playlistOwnerUid.trim();
        }
        if (fallbackUid != null && !fallbackUid.isBlank()) {
            return fallbackUid.trim();
        }
        throw new IllegalStateException("未配置 lycan.music.playlist-owner-uid");
    }

    /**
     * 带音质参数请求上游 URL 接口，返回可播放地址。
     */
    private TrackUrlFetchResult fetchTrackUrlWithLevel(String id, String level, boolean withCookie) {
        if (withCookie && !allowLoginUrlRequest()) {
            return TrackUrlFetchResult.limited();
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("id", id);
        query.put("level", level);
        query.put("timestamp", String.valueOf(System.currentTimeMillis()));
        appendCookie(query, withCookie);
        JsonNode node = upstreamClient.get("/song/url/v1", query);

        JsonNode dataArray = node.path("data");
        if (!dataArray.isArray() || dataArray.isEmpty()) {
            return TrackUrlFetchResult.empty();
        }
        JsonNode first = dataArray.get(0);
        String url = safeText(first, "url");
        String normalized = url.startsWith("http:") ? url.replace("http:", "https:") : url;
        return TrackUrlFetchResult.success(normalized);
    }

    private List<MusicTrackDto> fetchUserRecordTracks(String uid, int limit, boolean useAllData) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("uid", uid);
        query.put("type", "1");
        query.put("timestamp", String.valueOf(System.currentTimeMillis()));
        appendCookie(query);

        JsonNode node = upstreamClient.get("/user/record", query);
        int code = jsonNodeExtractors.findInt(node, "code").orElse(-1);
        if (code != 200) {
            throw new IllegalStateException("拉取听歌记录失败，返回码: " + code);
        }

        JsonNode recordArray = useAllData
                ? jsonNodeExtractors.findArray(node, "allData").orElse(null)
                : jsonNodeExtractors.findArray(node, "weekData").orElse(null);
        if (recordArray == null || !recordArray.isArray()) {
            return List.of();
        }

        List<MusicTrackDto> tracks = new ArrayList<>();
        for (JsonNode item : recordArray) {
            JsonNode song = item.has("song") ? item.get("song") : item;
            if (song == null || song.isNull()) {
                continue;
            }
            String id = safeText(song, "id");
            String name = safeText(song, "name");
            if (id.isBlank() || name.isBlank()) {
                continue;
            }
            tracks.add(new MusicTrackDto(
                    id,
                    name,
                    parseArtists(song.get("ar")),
                    safeText(song.path("al"), "picUrl")
            ));
            if (tracks.size() >= limit) {
                break;
            }
        }
        return tracks;
    }

    private Map<String, Object> toTrackUrlResponse(String songId, String preferred, TrackUrlResolveResult resolved) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", songId);
        data.put("url", resolved.url());
        data.put("level", resolved.level().isBlank() ? preferred : resolved.level());
        data.put("source", resolved.source());
        return data;
    }

    private boolean allowLoginUrlRequest() {
        return rateLimiter.allow(
                LOGIN_URL_GLOBAL_RATE_LIMIT_KEY,
                Math.max(1, loginUrlGlobalLimitPerMinute)
        );
    }

    private SongDetail getSongDetail(String songId) {
        CacheEntry<SongDetail> cached = songDetailCache.get(songId);
        long now = System.currentTimeMillis();
        if (cached != null && !cached.expired(now)) {
            return cached.value();
        }

        JsonNode detailNode = upstreamClient.get("/song/detail", Map.of("ids", songId));
        JsonNode songs = detailNode.path("songs");
        if (!songs.isArray() || songs.isEmpty()) {
            throw new IllegalStateException("未找到歌曲详情");
        }
        JsonNode song = songs.get(0);
        SongDetail detail = new SongDetail(
                songId,
                safeText(song, "name"),
                parseArtists(song.get("ar")),
                safeText(song.path("al"), "picUrl")
        );
        songDetailCache.put(songId, new CacheEntry<>(detail, now + SONG_DETAIL_CACHE_TTL_MS));
        return detail;
    }

    private TrackUrlResolveResult getCachedTrackUrl(String cacheKey) {
        CacheEntry<TrackUrlResolveResult> cached = resolvedTrackUrlCache.get(cacheKey);
        if (cached == null || cached.expired(System.currentTimeMillis())) {
            return null;
        }
        return cached.value();
    }

    private void cacheTrackUrl(String cacheKey, TrackUrlResolveResult value, long ttlMs) {
        resolvedTrackUrlCache.put(cacheKey, new CacheEntry<>(value, System.currentTimeMillis() + ttlMs));
    }

    private void cleanupCaches() {
        long now = System.currentTimeMillis();
        cleanupExpired(songDetailCache, now);
        cleanupExpired(resolvedTrackUrlCache, now);
        cleanupExpired(lyricCache, now);
    }

    private <T> void cleanupExpired(Map<String, CacheEntry<T>> cache, long now) {
        Iterator<Map.Entry<String, CacheEntry<T>>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CacheEntry<T>> entry = iterator.next();
            if (entry.getValue().expired(now)) {
                iterator.remove();
            }
        }
    }

    /**
     * 在有登录态时把 cookie 注入上游请求。
     */
    private void appendCookie(Map<String, String> query) {
        appendCookie(query, true);
    }

    private void appendCookie(Map<String, String> query, boolean enabled) {
        if (enabled && sessionService.hasCookie()) {
            query.put("cookie", sessionService.getRequiredCookie());
        }
    }

    private String validateSongId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id 参数不能为空");
        }
        return id.trim();
    }

    private String normalizeLevel(String level) {
        MusicQualityLevel configuredDefault = MusicQualityLevel.parseOrDefault(preferredLevel, MusicQualityLevel.EXHIGH);
        if (level == null || level.isBlank()) {
            return configuredDefault.value();
        }
        return MusicQualityLevel.parseOrDefault(level, configuredDefault).value();
    }

    /**
     * 构建音质尝试顺序：优先传入值，再补齐默认降级链路。
     */
    private List<String> buildLevelAttempts(String preferred) {
        return MusicQualityLevel.buildPublicAttemptOrder(preferred);
    }

    private String safeText(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }

    private String parseArtists(JsonNode artistsNode) {
        if (artistsNode == null || !artistsNode.isArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode artistNode : artistsNode) {
            String name = safeText(artistNode, "name");
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return String.join("/", names);
    }

    private MusicTrackLyricDto parseLyric(String songId, String lrcText) {
        if (lrcText == null || lrcText.isBlank()) {
            return new MusicTrackLyricDto(songId, false, List.of());
        }

        String[] rawLines = lrcText.split("\\r?\\n");
        List<MusicLyricLineDto> lines = new ArrayList<>();
        for (String rawLine : rawLines) {
            if (rawLine == null || rawLine.isBlank()) {
                continue;
            }
            Matcher matcher = LRC_LINE_PATTERN.matcher(rawLine);
            if (!matcher.matches()) {
                continue;
            }
            long timeMs = toLyricTimeMs(matcher.group(1), matcher.group(2), matcher.group(3));
            String text = matcher.group(4) == null ? "" : matcher.group(4).trim();
            if (text.isBlank()) {
                continue;
            }
            lines.add(new MusicLyricLineDto(timeMs, text));
        }

        lines.sort((left, right) -> Long.compare(left.timeMs(), right.timeMs()));
        return new MusicTrackLyricDto(songId, !lines.isEmpty(), lines);
    }

    private long toLyricTimeMs(String minuteText, String secondText, String fractionText) {
        int minutes = parseInt(minuteText);
        int seconds = parseInt(secondText);
        int milliseconds = parseFractionMillis(fractionText);
        return minutes * 60_000L + seconds * 1_000L + milliseconds;
    }

    private int parseFractionMillis(String fractionText) {
        if (fractionText == null || fractionText.isBlank()) {
            return 0;
        }
        String normalized = fractionText.trim();
        if (normalized.length() == 1) {
            normalized = normalized + "00";
        } else if (normalized.length() == 2) {
            normalized = normalized + "0";
        } else if (normalized.length() > 3) {
            normalized = normalized.substring(0, 3);
        }
        return parseInt(normalized);
    }

    private int parseInt(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record SongDetail(String id, String name, String artist, String cover) {
    }

    private record TrackUrlResolveResult(String url, String level, String source) {
        private static TrackUrlResolveResult success(String url, String level, String source) {
            return new TrackUrlResolveResult(url, level, source);
        }

        private static TrackUrlResolveResult failure(String level, String source) {
            return new TrackUrlResolveResult("", level, source);
        }
    }

    private record TrackUrlFetchResult(String url, boolean rateLimited) {
        private static TrackUrlFetchResult success(String url) {
            return new TrackUrlFetchResult(url, false);
        }

        private static TrackUrlFetchResult empty() {
            return new TrackUrlFetchResult("", false);
        }

        private static TrackUrlFetchResult limited() {
            return new TrackUrlFetchResult("", true);
        }
    }

    private record CacheEntry<T>(T value, long expireAtMs) {
        private boolean expired(long now) {
            return now >= expireAtMs;
        }
    }
}
