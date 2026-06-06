package com.lycanclaw.backend.waline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lycanclaw.backend.waline.config.WalineProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Waline 网关客户端。
 * 负责统一访问 Waline HTTP 接口并返回原始响应。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class WalineGatewayClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final WalineProperties properties;

    public WalineGatewayClient(ObjectMapper objectMapper, WalineProperties properties) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 拉取 Waline 最新评论原始响应。
     */
    public JsonNode fetchRecentComments(int limit) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("type", "recent");
        query.add("count", Integer.toString(Math.max(1, Math.min(limit, 50))));
        return get("/api/comment", query);
    }

    /**
     * 查询指定文章路径的评论数量。
     */
    public int fetchCommentCount(String path) {
        String normalizedPath = normalizePath(path);
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("type", "count");
        query.add("url", normalizedPath);
        JsonNode node = get("/api/comment", query);
        return parseCommentCount(node, normalizedPath);
    }

    /**
     * 查询指定文章路径的阅读量。
     */
    public int fetchPageview(String path) {
        String normalizedPath = normalizePath(path);
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("path", normalizedPath);
        JsonNode node = get("/api/article", query);
        return parseInteger(node);
    }

    /**
     * 增加指定文章路径的阅读量。
     */
    public int increasePageview(String path) {
        String normalizedPath = normalizePath(path);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("path", normalizedPath);
        JsonNode node = postJson("/api/article", body);
        return parseInteger(node);
    }

    /**
     * 使用 Waline 登录 token 查询当前用户信息。
     */
    public JsonNode fetchTokenProfile(String walineToken) {
        if (walineToken == null || walineToken.isBlank()) {
            throw new IllegalArgumentException("Waline token 不能为空");
        }
        URI uri = buildUri("/token", new LinkedMultiValueMap<>());
        HttpRequest request = buildGetRequest(uri)
                .header("Authorization", "Bearer " + walineToken.trim())
                .build();
        JsonNode node = send(request);
        int errno = node.path("errno").asInt(0);
        if (errno != 0) {
            String message = node.path("errmsg").asText("Waline token 校验失败");
            throw new IllegalArgumentException(message);
        }
        JsonNode data = node.path("data");
        return (data.isMissingNode() || data.isNull()) ? node : data;
    }

    private JsonNode get(String path, MultiValueMap<String, String> query) {
        URI uri = buildUri(path, query);
        HttpRequest request = buildGetRequest(uri).build();
        return send(request);
    }

    private JsonNode postJson(String path, JsonNode jsonBody) {
        URI uri = buildUri(path, new LinkedMultiValueMap<>());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "LycanClawBackend/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString(), StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Waline 请求失败，状态码: " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("解析 Waline 返回失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Waline 请求被中断", e);
        }
    }

    private HttpRequest.Builder buildGetRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("User-Agent", "LycanClawBackend/1.0")
                .GET();
    }

    private URI buildUri(String path, MultiValueMap<String, String> query) {
        String baseUrl = normalizeBaseUrl(properties.getBaseUrl());
        return UriComponentsBuilder
                .fromHttpUrl(baseUrl + path)
                .queryParams(query)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("未配置 Waline 服务地址 lycan.waline.base-url");
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 参数不能为空");
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    /**
     * 兼容 Waline count 接口可能返回的数字、数组或路径映射结构。
     */
    private int parseCommentCount(JsonNode node, String path) {
        JsonNode payload = unwrapData(node);
        if (payload == null || payload.isNull()) return 0;
        if (payload.isNumber()) return Math.max(0, payload.asInt(0));
        if (payload.isArray() && payload.size() > 0 && payload.get(0).isNumber()) {
            return Math.max(0, payload.get(0).asInt(0));
        }

        String noSlash = path.startsWith("/") ? path.substring(1) : path;
        String withSlash = path.startsWith("/") ? path : "/" + path;

        if (payload.has(path) && payload.get(path).isNumber()) return Math.max(0, payload.get(path).asInt(0));
        if (payload.has(noSlash) && payload.get(noSlash).isNumber()) return Math.max(0, payload.get(noSlash).asInt(0));
        if (payload.has(withSlash) && payload.get(withSlash).isNumber()) return Math.max(0, payload.get(withSlash).asInt(0));
        return 0;
    }

    private int parseInteger(JsonNode node) {
        JsonNode payload = unwrapData(node);
        if (payload == null || payload.isNull()) return 0;
        if (payload.isNumber()) return Math.max(0, payload.asInt(0));
        if (payload.isArray() && payload.size() > 0) {
            return parseIntegerValue(payload.get(0));
        }
        return parseIntegerValue(payload);
    }

    private int parseIntegerValue(JsonNode node) {
        if (node == null || node.isNull()) return 0;
        if (node.isNumber()) return Math.max(0, node.asInt(0));
        if (node.has("time") && node.get("time").isNumber()) return Math.max(0, node.get("time").asInt(0));
        if (node.has("count") && node.get("count").isNumber()) return Math.max(0, node.get("count").asInt(0));
        return 0;
    }

    private JsonNode unwrapData(JsonNode node) {
        if (node == null || node.isNull()) return node;
        JsonNode data = node.get("data");
        return data == null || data.isNull() ? node : data;
    }
}
