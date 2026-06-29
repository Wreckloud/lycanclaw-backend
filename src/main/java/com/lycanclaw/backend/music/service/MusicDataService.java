package com.lycanclaw.backend.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.common.exception.UpstreamServiceException;
import com.lycanclaw.backend.common.json.JsonNodeExtractors;
import com.lycanclaw.backend.common.security.InMemorySlidingWindowRateLimiter;
import com.lycanclaw.backend.music.config.MusicProperties;
import com.lycanclaw.backend.music.dto.MusicLyricLineDto;
import com.lycanclaw.backend.music.dto.MusicTrackDto;
import com.lycanclaw.backend.music.dto.MusicTrackLyricDto;
import com.lycanclaw.backend.music.model.MusicQualityLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    private static final long USER_RECORD_CACHE_TTL_MS = 10L * 60 * 1000;
    private static final int USER_RECORD_CACHE_LIMIT = 200;
    private static final Pattern SONG_ID_PATTERN = Pattern.compile("\\d{1,20}");
    private static final Pattern LRC_LINE_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\](.*)");

    @Value("${lycan.security.music-login-url-global-limit-per-minute:60}")
    private int loginUrlGlobalLimitPerMinute;

    private final NcmUpstreamClient upstreamClient;
    private final MusicAuthSessionService sessionService;
    private final JsonNodeExtractors jsonNodeExtractors;
    private final InMemorySlidingWindowRateLimiter rateLimiter;
    private final MusicProperties musicProperties;
    private final Map<String, CacheEntry<SongDetail>> songDetailCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<TrackUrlResolveResult>> resolvedTrackUrlCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<MusicTrackLyricDto>> lyricCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<MusicTrackDto>>> userRecordCache = new ConcurrentHashMap<>();

    public MusicDataService(
            NcmUpstreamClient upstreamClient,
            MusicAuthSessionService sessionService,
            JsonNodeExtractors jsonNodeExtractors,
            InMemorySlidingWindowRateLimiter rateLimiter,
            MusicProperties musicProperties
    ) {
        this.upstreamClient = upstreamClient;
        this.sessionService = sessionService;
        this.jsonNodeExtractors = jsonNodeExtractors;
        this.rateLimiter = rateLimiter;
        this.musicProperties = musicProperties;
    }

    /**
     * 拉取运维配置账号的周听歌榜，不向前端暴露用户 UID。
     */
    public Map<String, Object> getWeeklyRanking(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String uid = resolveRankingOwnerUid();
        List<MusicTrackDto> tracks = limitTracks(loadUserRecordTracks(uid, false), safeLimit);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("limit", safeLimit);
        data.put("tracks", tracks);
        data.put("source", "user-record");
        return data;
    }

    /**
     * 查询首页随机池（先 weekData，不足再补 allData）。
     */
    public List<MusicTrackDto> getHomeTrackPool(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String uid = resolveRankingOwnerUid();

        List<MusicTrackDto> weekTracks = limitTracks(loadUserRecordTracks(uid, false), safeLimit);
        if (weekTracks.size() >= safeLimit) {
            return weekTracks;
        }

        List<MusicTrackDto> allTracks = limitTracks(loadUserRecordTracks(uid, true), safeLimit);
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
        return limitTracks(loadUserRecordTracks(resolveRankingOwnerUid(), false), safeLimit);
    }

    /**
     * 获取歌曲播放地址：
     * 1) 先用公开模式尝试（不带 cookie）；
     * 2) 公开全部失败后，再尝试登录模式（带 cookie）。
     */
    private Map<String, Object> resolveTrackUrl(String id, String level) {
        String safeId = validateSongId(id);
        String preferred = normalizeLevel(level);
        boolean hasLoginCookie = sessionService.hasCookie();
        String cacheKey = safeId + "|" + preferred + "|" + (hasLoginCookie ? "auth" : "anon");

        cleanupCaches();
        TrackUrlResolveResult cached = getCachedTrackUrl(cacheKey);
        if (cached != null) {
            return toTrackUrlResponse(safeId, preferred, cached);
        }

        SongDetail detail = getSongDetail(safeId);
        long songDurationMs = detail.durationMs();
        List<String> levels = buildLevelAttempts(preferred);
        TrackUrlFetchResult firstTrialResult = null;

        for (String attemptLevel : levels) {
            TrackUrlFetchResult publicResult = fetchTrackUrlWithLevel(safeId, attemptLevel, false, songDurationMs);
            if (!publicResult.playable()) {
                continue;
            }
            if (!publicResult.trial()) {
                TrackUrlResolveResult resolved = TrackUrlResolveResult.success(publicResult.url(), attemptLevel, "public", false);
                cacheTrackUrl(cacheKey, resolved, TRACK_URL_PUBLIC_CACHE_TTL_MS);
                return toTrackUrlResponse(safeId, preferred, resolved);
            }
            if (firstTrialResult == null) {
                firstTrialResult = publicResult;
            }
        }

        if (!hasLoginCookie) {
            if (firstTrialResult != null) {
                TrackUrlResolveResult trial = TrackUrlResolveResult.success(
                        firstTrialResult.url(),
                        firstTrialResult.level(),
                        "public_trial",
                        true
                );
                cacheTrackUrl(cacheKey, trial, TRACK_URL_FAILURE_CACHE_TTL_MS);
                return toTrackUrlResponse(safeId, preferred, trial);
            }
            TrackUrlResolveResult failed = TrackUrlResolveResult.failure(preferred, "public_only");
            cacheTrackUrl(cacheKey, failed, TRACK_URL_FAILURE_CACHE_TTL_MS);
            return toTrackUrlResponse(safeId, preferred, failed);
        }

        boolean loginRateLimited = false;
        for (String attemptLevel : levels) {
            TrackUrlFetchResult authResult = fetchTrackUrlWithLevel(safeId, attemptLevel, true, songDurationMs);
            if (authResult.rateLimited()) {
                loginRateLimited = true;
                continue;
            }
            if (authResult.playable()) {
                TrackUrlResolveResult resolved = TrackUrlResolveResult.success(
                        authResult.url(),
                        attemptLevel,
                        authResult.trial() ? "login_trial" : "login",
                        authResult.trial()
                );
                cacheTrackUrl(cacheKey, resolved, TRACK_URL_LOGIN_CACHE_TTL_MS);
                return toTrackUrlResponse(safeId, preferred, resolved);
            }
        }

        if (firstTrialResult != null) {
            TrackUrlResolveResult trial = TrackUrlResolveResult.success(
                    firstTrialResult.url(),
                    firstTrialResult.level(),
                    "public_trial_after_login_failed",
                    true
            );
            cacheTrackUrl(cacheKey, trial, TRACK_URL_FAILURE_CACHE_TTL_MS);
            return toTrackUrlResponse(safeId, preferred, trial);
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
        String preferredLevel = normalizeLevel(level);
        SongDetail detail = getSongDetail(safeId);
        Map<String, Object> urlInfo = resolveTrackUrl(safeId, preferredLevel);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", detail.id());
        data.put("name", detail.name());
        data.put("artist", detail.artist());
        data.put("cover", detail.cover());
        data.put("url", urlInfo.get("url"));
        data.put("level", urlInfo.get("level"));
        data.put("source", urlInfo.get("source"));
        data.put("trial", urlInfo.get("trial"));
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

        String lrcText = requestLyricText(safeId);
        MusicTrackLyricDto parsed = parseLyric(safeId, lrcText);
        lyricCache.put(safeId, new CacheEntry<>(parsed, now + LYRIC_CACHE_TTL_MS));
        return parsed;
    }

    /**
     * 拉取歌词文本：
     * 1) 优先 /lyric；
     * 2) 404 或结构异常时回退 /lyric/new。
     */
    private String requestLyricText(String songId) {
        String primary = readLyricTextFromEndpoint("/lyric", songId);
        if (!primary.isBlank()) {
            return primary;
        }
        return readLyricTextFromEndpoint("/lyric/new", songId);
    }

    private String readLyricTextFromEndpoint(String endpoint, String songId) {
        try {
            JsonNode lyricNode = upstreamClient.get(endpoint, Map.of("id", songId));
            JsonNode lrcNode = lyricNode.path("lrc");
            return safeText(lrcNode, "lyric");
        } catch (UpstreamServiceException ex) {
            if (is404StatusError(ex)) {
                return "";
            }
            throw ex;
        }
    }

    private boolean is404StatusError(UpstreamServiceException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("状态码: 404");
    }

    private String resolveRankingOwnerUid() {
        String rankingOwnerUid = musicProperties.getRankingOwnerUid() == null
                ? ""
                : musicProperties.getRankingOwnerUid().trim();
        if (rankingOwnerUid.matches("\\d{1,20}")) {
            return rankingOwnerUid;
        }
        throw new IllegalStateException("lycan.music.ranking-owner-uid 必须配置为有效的网易云用户 ID");
    }

    /**
     * 带音质参数请求上游 URL 接口，返回可播放地址。
     */
    private TrackUrlFetchResult fetchTrackUrlWithLevel(String id, String level, boolean withCookie, long songDurationMs) {
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
        if (normalized.isBlank()) {
            return TrackUrlFetchResult.empty();
        }
        return TrackUrlFetchResult.success(normalized, level, isTrialUrl(first, songDurationMs));
    }

    private List<MusicTrackDto> loadUserRecordTracks(String uid, boolean useAllData) {
        cleanupCaches();
        String cacheKey = uid + "|" + (useAllData ? "all" : "week");
        CacheEntry<List<MusicTrackDto>> cached = userRecordCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && !cached.expired(now)) {
            return cached.value();
        }

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
            List<MusicTrackDto> empty = List.of();
            userRecordCache.put(cacheKey, new CacheEntry<>(empty, now + USER_RECORD_CACHE_TTL_MS));
            return empty;
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
            if (tracks.size() >= USER_RECORD_CACHE_LIMIT) {
                break;
            }
        }
        List<MusicTrackDto> result = List.copyOf(tracks);
        userRecordCache.put(cacheKey, new CacheEntry<>(result, now + USER_RECORD_CACHE_TTL_MS));
        return result;
    }

    private List<MusicTrackDto> limitTracks(List<MusicTrackDto> tracks, int limit) {
        return List.copyOf(tracks.subList(0, Math.min(limit, tracks.size())));
    }

    private Map<String, Object> toTrackUrlResponse(String songId, String preferred, TrackUrlResolveResult resolved) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", songId);
        data.put("url", resolved.url());
        data.put("level", resolved.level().isBlank() ? preferred : resolved.level());
        data.put("source", resolved.source());
        data.put("trial", resolved.trial());
        return data;
    }

    /**
     * 判断公开地址是否只是试听片段。
     * 优先使用上游 freeTrial* 标记，缺失时再用可播放时长和歌曲总时长兜底。
     */
    private boolean isTrialUrl(JsonNode node, long songDurationMs) {
        if (node == null || node.isNull()) {
            return false;
        }

        if (node.hasNonNull("freeTrialInfo") && !node.path("freeTrialInfo").isNull()) {
            return true;
        }

        JsonNode freeTrialPrivilege = node.path("freeTrialPrivilege");
        if (!freeTrialPrivilege.isMissingNode() && !freeTrialPrivilege.isNull()) {
            if (freeTrialPrivilege.path("resConsumable").asBoolean(false)
                    || freeTrialPrivilege.path("userConsumable").asBoolean(false)
                    || hasTextValue(freeTrialPrivilege, "listenType")
                    || hasTextValue(freeTrialPrivilege, "cannotListenReason")
                    || hasTextValue(freeTrialPrivilege, "playReason")
                    || hasTextValue(freeTrialPrivilege, "freeLimitTagType")) {
                return true;
            }
        }

        JsonNode freeTimeTrialPrivilege = node.path("freeTimeTrialPrivilege");
        if (!freeTimeTrialPrivilege.isMissingNode() && !freeTimeTrialPrivilege.isNull()) {
            if (freeTimeTrialPrivilege.path("type").asInt(0) > 0
                    || freeTimeTrialPrivilege.path("remainTime").asLong(0L) > 0) {
                return true;
            }
        }

        long playableTimeMs = node.path("time").asLong(0L);
        return songDurationMs > 0
                && playableTimeMs > 0
                && playableTimeMs <= 120_000L
                && playableTimeMs < Math.round(songDurationMs * 0.8D);
    }

    private boolean hasTextValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return !value.isMissingNode() && !value.isNull() && !value.asText("").isBlank();
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
                safeText(song.path("al"), "picUrl"),
                song.path("dt").asLong(0L)
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
        cleanupExpired(userRecordCache, now);
    }

    private <T> void cleanupExpired(Map<String, CacheEntry<T>> cache, long now) {
        cache.entrySet().removeIf(entry -> entry.getValue().expired(now));
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
        String normalized = id == null ? "" : id.trim();
        if (!SONG_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("id 必须是有效的网易云歌曲 ID");
        }
        return normalized;
    }

    private String normalizeLevel(String level) {
        MusicQualityLevel configuredDefault = MusicQualityLevel.fromValue(musicProperties.getPreferredLevel())
                .orElseThrow(() -> new IllegalStateException("lycan.music.preferred-level 配置无效"));
        if (level == null || level.isBlank()) {
            return configuredDefault.value();
        }
        return MusicQualityLevel.fromValue(level)
                .orElseThrow(() -> new IllegalArgumentException("不支持的音质级别: " + level.trim()))
                .value();
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

    private record SongDetail(String id, String name, String artist, String cover, long durationMs) {
    }

    private record TrackUrlResolveResult(String url, String level, String source, boolean trial) {
        private static TrackUrlResolveResult success(String url, String level, String source, boolean trial) {
            return new TrackUrlResolveResult(url, level, source, trial);
        }

        private static TrackUrlResolveResult failure(String level, String source) {
            return new TrackUrlResolveResult("", level, source, false);
        }
    }

    private record TrackUrlFetchResult(String url, String level, boolean trial, boolean rateLimited) {
        private boolean playable() {
            return !url.isBlank();
        }

        private static TrackUrlFetchResult success(String url, String level, boolean trial) {
            return new TrackUrlFetchResult(url, level, trial, false);
        }

        private static TrackUrlFetchResult empty() {
            return new TrackUrlFetchResult("", "", false, false);
        }

        private static TrackUrlFetchResult limited() {
            return new TrackUrlFetchResult("", "", false, true);
        }
    }

    private record CacheEntry<T>(T value, long expireAtMs) {
        private boolean expired(long now) {
            return now >= expireAtMs;
        }
    }
}
