package com.lycanclaw.backend.runtimeconfig.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.music.config.MusicUpstreamProperties;
import com.lycanclaw.backend.music.model.MusicQualityLevel;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.runtimeconfig.dto.RuntimeConfigDto;
import com.lycanclaw.backend.runtimeconfig.dto.RuntimeConfigResponseDto;
import com.lycanclaw.backend.tag.config.TagProperties;
import com.lycanclaw.backend.waline.config.WalineProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时配置服务。
 * 负责读取、校验和持久化后台可编辑的非敏感配置，并为业务服务提供即时生效的配置值。
 * @author Wreckloud
 * @since 2026-06-03
 */
@Service
public class RuntimeConfigService {

    private final ObjectMapper objectMapper;
    private final RecommendationProperties recommendationProperties;
    private final TagProperties tagProperties;
    private final WalineProperties walineProperties;
    private final MusicUpstreamProperties musicUpstreamProperties;
    private final AppTimeProvider appTimeProvider;

    @Value("${lycan.admin.runtime-config-path:data/admin-runtime-config.json}")
    private String runtimeConfigPath;

    @Value("${lycan.music.playlist-owner-uid:629126546}")
    private String defaultPlaylistOwnerUid;

    @Value("${lycan.music.preferred-level:exhigh}")
    private String defaultPreferredLevel;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${lycan.security.admin-token:}")
    private String adminToken;

    @Value("${lycan.security.admin-qq-whitelist:}")
    private String adminQqWhitelist;

    @Value("${lycan.security.admin-require-waline-administrator:true}")
    private boolean adminRequireWalineAdministrator;

    @Value("${lycan.security.auth-rate-limit-per-minute:30}")
    private int adminRateLimitPerMinute;

    @Value("${lycan.security.music-rate-limit-per-minute:30}")
    private int musicRateLimitPerMinute;

    @Value("${lycan.security.music-login-url-global-limit-per-minute:60}")
    private int musicLoginUrlGlobalLimitPerMinute;

    @Value("${lycan.security.admin-auth-log-enabled:true}")
    private boolean adminAuthLogEnabled;

    @Value("${lycan.security.public-access-log-enabled:true}")
    private boolean publicAccessLogEnabled;

    @Value("${lycan.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    private volatile RuntimeConfigFile cachedFile;

    public RuntimeConfigService(
            ObjectMapper objectMapper,
            RecommendationProperties recommendationProperties,
            TagProperties tagProperties,
            WalineProperties walineProperties,
            MusicUpstreamProperties musicUpstreamProperties,
            AppTimeProvider appTimeProvider
    ) {
        this.objectMapper = objectMapper;
        this.recommendationProperties = recommendationProperties;
        this.tagProperties = tagProperties;
        this.walineProperties = walineProperties;
        this.musicUpstreamProperties = musicUpstreamProperties;
        this.appTimeProvider = appTimeProvider;
    }

    /**
     * 返回管理端配置视图。
     */
    public RuntimeConfigResponseDto view() {
        RuntimeConfigFile file = loadFile();
        return toResponse(file);
    }

    /**
     * 保存新的运行时配置，配置校验通过后立即生效。
     */
    public synchronized RuntimeConfigResponseDto update(RuntimeConfigDto request) {
        RuntimeConfigDto normalized = normalize(request);
        RuntimeConfigFile file = new RuntimeConfigFile(normalized, true, appTimeProvider.nowOffsetString());
        writeFile(file);
        cachedFile = file;
        return toResponse(file);
    }

    /**
     * 删除运行时配置文件，恢复 application.yml 默认值。
     */
    public synchronized RuntimeConfigResponseDto reset() {
        Path path = configPath();
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("删除运行时配置失败", ex);
        }
        RuntimeConfigFile file = new RuntimeConfigFile(defaultConfig(), false, "");
        cachedFile = file;
        return toResponse(file);
    }

    public String playlistOwnerUid() {
        return loadFile().config().music().playlistOwnerUid();
    }

    public String preferredLevel() {
        return loadFile().config().music().preferredLevel();
    }

    public int maxCandidatePosts() {
        return loadFile().config().recommendation().maxCandidatePosts();
    }

    public int snapshotFreshSeconds() {
        return loadFile().config().recommendation().snapshotFreshSeconds();
    }

    public double pageviewWeight() {
        return loadFile().config().recommendation().pageviewWeight();
    }

    public double commentWeight() {
        return loadFile().config().recommendation().commentWeight();
    }

    public int tagCacheSeconds() {
        return loadFile().config().tag().tagCacheSeconds();
    }

    private RuntimeConfigResponseDto toResponse(RuntimeConfigFile file) {
        return new RuntimeConfigResponseDto(
                file.config(),
                defaultConfig(),
                readonlySummary(),
                file.stored(),
                file.updatedAt()
        );
    }

    private RuntimeConfigFile loadFile() {
        RuntimeConfigFile current = cachedFile;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (cachedFile != null) {
                return cachedFile;
            }
            cachedFile = readFile();
            return cachedFile;
        }
    }

    private RuntimeConfigFile readFile() {
        Path path = configPath();
        if (!Files.exists(path)) {
            return new RuntimeConfigFile(defaultConfig(), false, "");
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            JsonNode configNode = root.has("config") ? root.path("config") : root;
            RuntimeConfigDto config = objectMapper.treeToValue(configNode, RuntimeConfigDto.class);
            String updatedAt = root.path("updatedAt").asText("");
            return new RuntimeConfigFile(normalize(config), true, updatedAt);
        } catch (Exception ex) {
            throw new IllegalStateException("读取运行时配置失败: " + path, ex);
        }
    }

    private void writeFile(RuntimeConfigFile file) {
        Path path = configPath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("updatedAt", file.updatedAt());
            payload.put("config", file.config());
            Files.writeString(tempPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
            moveTempFile(tempPath, path);
        } catch (IOException ex) {
            throw new IllegalStateException("写入运行时配置失败: " + path, ex);
        }
    }

    private void moveTempFile(Path tempPath, Path path) throws IOException {
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private RuntimeConfigDto normalize(RuntimeConfigDto request) {
        RuntimeConfigDto defaults = defaultConfig();
        RuntimeConfigDto.Music music = request == null || request.music() == null ? defaults.music() : request.music();
        RuntimeConfigDto.Recommendation recommendation = request == null || request.recommendation() == null
                ? defaults.recommendation()
                : request.recommendation();
        RuntimeConfigDto.Tag tag = request == null || request.tag() == null ? defaults.tag() : request.tag();

        String playlistOwnerUid = requireNumericUid(music.playlistOwnerUid(), defaults.music().playlistOwnerUid(), "playlistOwnerUid");
        String preferredLevel = normalizeQualityLevel(music.preferredLevel(), defaults.music().preferredLevel());

        return new RuntimeConfigDto(
                new RuntimeConfigDto.Music(playlistOwnerUid, preferredLevel),
                new RuntimeConfigDto.Recommendation(
                        clampInt(recommendation.maxCandidatePosts(), defaults.recommendation().maxCandidatePosts(), 1, 1000, "maxCandidatePosts"),
                        clampInt(recommendation.snapshotFreshSeconds(), defaults.recommendation().snapshotFreshSeconds(), 60, 86_400, "snapshotFreshSeconds"),
                        clampDouble(recommendation.pageviewWeight(), defaults.recommendation().pageviewWeight(), 0, 1000, "pageviewWeight"),
                        clampDouble(recommendation.commentWeight(), defaults.recommendation().commentWeight(), 0, 1000, "commentWeight")
                ),
                new RuntimeConfigDto.Tag(
                        clampInt(tag.tagCacheSeconds(), defaults.tag().tagCacheSeconds(), 5, 86_400, "tagCacheSeconds")
                )
        );
    }

    private RuntimeConfigDto defaultConfig() {
        String normalizedDefaultLevel = MusicQualityLevel.parseOrDefault(defaultPreferredLevel, MusicQualityLevel.EXHIGH).value();
        return new RuntimeConfigDto(
                new RuntimeConfigDto.Music(
                        defaultPlaylistOwnerUid == null ? "" : defaultPlaylistOwnerUid.trim(),
                        normalizedDefaultLevel
                ),
                new RuntimeConfigDto.Recommendation(
                        Math.max(1, recommendationProperties.getMaxCandidatePosts()),
                        Math.max(60, recommendationProperties.getSnapshotFreshSeconds()),
                        Math.max(0, recommendationProperties.getScore().getPageviewWeight()),
                        Math.max(0, recommendationProperties.getScore().getCommentWeight())
                ),
                new RuntimeConfigDto.Tag(
                        Math.max(5, tagProperties.getCacheSeconds())
                )
        );
    }

    private Map<String, Object> readonlySummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("database", Map.of(
                "url", datasourceUrl,
                "username", datasourceUsername,
                "passwordConfigured", datasourcePassword != null && !datasourcePassword.isBlank()
        ));
        result.put("services", Map.of(
                "walineBaseUrl", safeString(walineProperties.getBaseUrl()),
                "ncmUpstreamBaseUrl", safeString(musicUpstreamProperties.getBaseUrl())
        ));
        result.put("paths", Map.of(
                "recommendationPostsJsonPath", recommendationProperties.getPostsJsonPath(),
                "recommendationManualConfigPath", recommendationProperties.getManualConfigPath(),
                "tagPostsJsonPath", tagProperties.getPostsJsonPath()
        ));
        result.put("security", Map.of(
                "adminTokenConfigured", adminToken != null && !adminToken.isBlank(),
                "adminQqWhitelist", adminQqWhitelist,
                "adminRequireWalineAdministrator", adminRequireWalineAdministrator,
                "adminRateLimitPerMinute", adminRateLimitPerMinute,
                "musicRateLimitPerMinute", musicRateLimitPerMinute,
                "musicLoginUrlGlobalLimitPerMinute", musicLoginUrlGlobalLimitPerMinute,
                "adminAuthLogEnabled", adminAuthLogEnabled,
                "publicAccessLogEnabled", publicAccessLogEnabled,
                "trustForwardedHeaders", trustForwardedHeaders
        ));
        return result;
    }

    private String requireNumericUid(String value, String fallback, String fieldName) {
        String resolved = value == null || value.isBlank() ? fallback : value.trim();
        if (resolved == null || resolved.isBlank() || !resolved.matches("\\d+")) {
            throw new IllegalArgumentException(fieldName + " 必须是数字 UID");
        }
        return resolved;
    }

    private String normalizeQualityLevel(String value, String fallback) {
        String resolved = value == null || value.isBlank() ? fallback : value.trim();
        return MusicQualityLevel.fromValue(resolved)
                .orElseThrow(() -> new IllegalArgumentException("preferredLevel 不受支持: " + resolved))
                .value();
    }

    private int clampInt(Integer value, int fallback, int min, int max, String fieldName) {
        int resolved = value == null ? fallback : value;
        if (resolved < min || resolved > max) {
            throw new IllegalArgumentException(fieldName + " 必须在 " + min + " 到 " + max + " 之间");
        }
        return resolved;
    }

    private double clampDouble(Double value, double fallback, double min, double max, String fieldName) {
        double resolved = value == null ? fallback : value;
        if (!Double.isFinite(resolved) || resolved < min || resolved > max) {
            throw new IllegalArgumentException(fieldName + " 必须在 " + min + " 到 " + max + " 之间");
        }
        return resolved;
    }

    private Path configPath() {
        return Path.of(runtimeConfigPath);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private record RuntimeConfigFile(RuntimeConfigDto config, boolean stored, String updatedAt) {
    }
}
