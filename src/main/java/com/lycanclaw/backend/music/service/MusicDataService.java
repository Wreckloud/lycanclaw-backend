package com.lycanclaw.backend.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.music.dto.MusicTrackDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MusicDataService {

    private static final List<String> LEVEL_FALLBACK_ORDER = List.of(
            "jymaster",
            "exhigh",
            "lossless",
            "hires",
            "standard"
    );

    @Value("${lycan.music.fallback-uid:}")
    private String fallbackUid;

    @Value("${lycan.music.preferred-level:jymaster}")
    private String preferredLevel;

    private final NcmUpstreamClient upstreamClient;
    private final MusicAuthSessionService sessionService;

    public MusicDataService(NcmUpstreamClient upstreamClient, MusicAuthSessionService sessionService) {
        this.upstreamClient = upstreamClient;
        this.sessionService = sessionService;
    }

    public Map<String, Object> getWeeklyRanking(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String uid = resolveUid();

        Map<String, String> query = new LinkedHashMap<>();
        query.put("uid", uid);
        query.put("type", "1");
        query.put("timestamp", String.valueOf(System.currentTimeMillis()));
        appendCookie(query);

        JsonNode node = upstreamClient.get("/user/record", query);
        int code = findInt(node, "code").orElse(-1);
        if (code != 200) {
            throw new IllegalStateException("拉取周听歌榜失败，返回码: " + code);
        }

        JsonNode weekData = findArray(node, "weekData")
                .orElseThrow(() -> new IllegalStateException("周听歌榜数据结构异常，缺少 weekData"));

        List<MusicTrackDto> tracks = new ArrayList<>();
        for (JsonNode item : weekData) {
            JsonNode song = item.has("song") ? item.get("song") : item;
            if (song == null || song.isNull()) continue;
            String id = safeText(song, "id");
            String name = safeText(song, "name");
            if (id.isBlank() || name.isBlank()) continue;
            tracks.add(new MusicTrackDto(
                    id,
                    name,
                    parseArtists(song.get("ar")),
                    safeText(song.path("al"), "picUrl")
            ));
            if (tracks.size() >= safeLimit) {
                break;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("limit", safeLimit);
        data.put("uid", uid);
        data.put("tracks", tracks);
        data.put("来源", sessionService.hasCookie() ? "当前登录账号" : "配置回退账号");
        return data;
    }

    public Map<String, Object> getTrackUrl(String id, String level) {
        String safeId = validateSongId(id);
        String preferred = normalizeLevel(level);

        List<String> levels = buildLevelAttempts(preferred);
        for (String attemptLevel : levels) {
            String url = fetchTrackUrlWithLevel(safeId, attemptLevel);
            if (!url.isBlank()) {
                return Map.of(
                        "id", safeId,
                        "url", url,
                        "level", attemptLevel
                );
            }
        }

        return Map.of(
                "id", safeId,
                "url", "",
                "level", preferred
        );
    }

    public Map<String, Object> getTrackDetailWithUrl(String id, String level) {
        String safeId = validateSongId(id);
        JsonNode detailNode = upstreamClient.get("/song/detail", Map.of("ids", safeId));

        JsonNode songs = detailNode.path("songs");
        if (!songs.isArray() || songs.isEmpty()) {
            throw new IllegalStateException("未找到歌曲详情");
        }
        JsonNode song = songs.get(0);
        Map<String, Object> urlInfo = getTrackUrl(safeId, level);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", safeId);
        data.put("name", safeText(song, "name"));
        data.put("artist", parseArtists(song.get("ar")));
        data.put("cover", safeText(song.path("al"), "picUrl"));
        data.put("url", urlInfo.get("url"));
        data.put("level", urlInfo.get("level"));
        return data;
    }

    private String resolveUid() {
        if (sessionService.hasCookie()) {
            String uid = resolveUidFromLoginStatus(sessionService.getCookie());
            if (!uid.isBlank()) return uid;
        }
        if (fallbackUid != null && !fallbackUid.isBlank()) {
            return fallbackUid.trim();
        }
        throw new IllegalStateException("未检测到登录账号，且未配置 lycan.music.fallback-uid");
    }

    private String resolveUidFromLoginStatus(String cookie) {
        JsonNode node = upstreamClient.get("/login/status", Map.of(
                "cookie", cookie,
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));
        return findText(node, "id").orElse("");
    }

    private String fetchTrackUrlWithLevel(String id, String level) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("id", id);
        query.put("level", level);
        query.put("timestamp", String.valueOf(System.currentTimeMillis()));
        appendCookie(query);
        JsonNode node = upstreamClient.get("/song/url/v1", query);

        JsonNode dataArray = node.path("data");
        if (!dataArray.isArray() || dataArray.isEmpty()) {
            return "";
        }
        JsonNode first = dataArray.get(0);
        String url = safeText(first, "url");
        return url.startsWith("http:") ? url.replace("http:", "https:") : url;
    }

    private void appendCookie(Map<String, String> query) {
        if (sessionService.hasCookie()) {
            query.put("cookie", sessionService.getCookie());
        }
    }

    private String validateSongId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id 参数不能为空");
        }
        return id.trim();
    }

    private String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return preferredLevel;
        }
        return level.trim();
    }

    private List<String> buildLevelAttempts(String preferred) {
        List<String> levels = new ArrayList<>();
        levels.add(preferred);
        for (String fallback : LEVEL_FALLBACK_ORDER) {
            if (!levels.contains(fallback)) {
                levels.add(fallback);
            }
        }
        return levels;
    }

    private Optional<String> findText(JsonNode node, String key) {
        if (node == null || node.isNull()) return Optional.empty();
        if (node.has(key) && !node.get(key).isNull()) {
            return Optional.of(node.get(key).asText(""));
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                Optional<String> child = findText(entry.getValue(), key);
                if (child.isPresent() && !child.get().isBlank()) return child;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<String> parsed = findText(child, key);
                if (parsed.isPresent() && !parsed.get().isBlank()) return parsed;
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> findInt(JsonNode node, String key) {
        Optional<String> text = findText(node, key);
        if (text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(text.get()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Optional<JsonNode> findArray(JsonNode node, String key) {
        if (node == null || node.isNull()) return Optional.empty();
        if (node.has(key) && node.get(key).isArray()) {
            return Optional.of(node.get(key));
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                Optional<JsonNode> child = findArray(entry.getValue(), key);
                if (child.isPresent()) return child;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<JsonNode> parsed = findArray(child, key);
                if (parsed.isPresent()) return parsed;
            }
        }
        return Optional.empty();
    }

    private String safeText(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }

    private String parseArtists(JsonNode artistsNode) {
        if (artistsNode == null || !artistsNode.isArray()) return "";
        List<String> names = new ArrayList<>();
        for (JsonNode artistNode : artistsNode) {
            String name = safeText(artistNode, "name");
            if (!name.isBlank()) names.add(name);
        }
        return String.join("/", names);
    }
}
