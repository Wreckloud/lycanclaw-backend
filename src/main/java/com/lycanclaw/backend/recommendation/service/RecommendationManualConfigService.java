package com.lycanclaw.backend.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.recommendation.config.RecommendationProperties;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 手动推荐配置服务。
 * 维护手动推荐配置的读写、校验与持久化。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class RecommendationManualConfigService {

    private static final String THOUGHT_URL_PREFIX = "/thoughts/";
    private static final String THOUGHT_URL_SUFFIX = ".html";

    private final ObjectMapper objectMapper;
    private final RecommendationProperties properties;
    private final AppTimeProvider appTimeProvider;

    public RecommendationManualConfigService(
            ObjectMapper objectMapper,
            RecommendationProperties properties,
            AppTimeProvider appTimeProvider
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.appTimeProvider = appTimeProvider;
    }
    /**
     * 读取当前手动推荐配置。
     */
    public synchronized RecommendationManualConfigDto read() {
        Path path = resolveConfigPath();
        if (!Files.exists(path)) {
            return new RecommendationManualConfigDto(List.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            List<String> manualUrls = new ArrayList<>();
            JsonNode urlsNode = root.path("manualUrls");
            if (urlsNode.isArray()) {
                for (JsonNode urlNode : urlsNode) {
                    String value = normalizeUrl(urlNode.asText(null));
                    if (value != null) {
                        manualUrls.add(value);
                    }
                }
            }
            return new RecommendationManualConfigDto(deduplicate(manualUrls), root.path("updatedAt").asText(null));
        } catch (IOException e) {
            throw new IllegalStateException("读取手动推荐配置失败", e);
        }
    }
    /**
     * 更新手动推荐 URL 列表，并写入配置文件。
     */
    public synchronized RecommendationManualConfigDto update(List<String> manualUrls) {
        List<String> sanitized = sanitizeManualUrls(manualUrls);
        String updatedAt = appTimeProvider.nowOffsetString();

        Path path = resolveConfigPath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode arrayNode = root.putArray("manualUrls");
            for (String url : sanitized) {
                arrayNode.add(url);
            }
            root.put("updatedAt", updatedAt);

            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            return new RecommendationManualConfigDto(sanitized, updatedAt);
        } catch (IOException e) {
            throw new IllegalStateException("写入手动推荐配置失败", e);
        }
    }

    private List<String> sanitizeManualUrls(List<String> manualUrls) {
        if (manualUrls == null) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String raw : manualUrls) {
            String value = normalizeUrl(raw);
            if (value == null) {
                continue;
            }
            if (!isValidThoughtPostUrl(value)) {
                throw new IllegalArgumentException("手动推荐链接格式无效，仅支持 /thoughts/*.html");
            }
            normalized.add(value);
        }
        return deduplicate(normalized);
    }

    private String normalizeUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private List<String> deduplicate(List<String> values) {
        Set<String> unique = new LinkedHashSet<>(values);
        return new ArrayList<>(unique);
    }

    private boolean isValidThoughtPostUrl(String value) {
        if (!value.startsWith(THOUGHT_URL_PREFIX)) {
            return false;
        }
        if (!value.endsWith(THOUGHT_URL_SUFFIX)) {
            return false;
        }
        if (value.endsWith("/index.html") || value.endsWith("/tags.html")) {
            return false;
        }
        return value.length() > (THOUGHT_URL_PREFIX.length() + THOUGHT_URL_SUFFIX.length());
    }

    private Path resolveConfigPath() {
        return Path.of(properties.getManualConfigPath());
    }
}
