package com.lycanclaw.backend.waline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * @Description Waline HTTP 网关客户端
 * @Author Wreckloud
 * @Date 2026-05-15
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

    public JsonNode fetchRecentComments(int limit) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("type", "recent");
        query.add("count", Integer.toString(Math.max(1, Math.min(limit, 50))));
        return get("/comment", query);
    }

    public int fetchCommentCount(String path) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("type", "count");
        query.add("url", path);
        JsonNode node = get("/comment", query);
        return parseCommentCount(node, path);
    }

    public int fetchPageview(String path) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("path", path);
        JsonNode node = get("/article", query);
        return parseInteger(node);
    }

    public int increasePageview(String path) {
        JsonNode node = postJson("/article", "{\"path\":\"" + escapeJson(path) + "\"}");
        return parseInteger(node);
    }

    private JsonNode get(String path, MultiValueMap<String, String> query) {
        URI uri = buildUri(path, query);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("User-Agent", "LycanClawBackend/1.0")
                .GET()
                .build();
        return send(request);
    }

    private JsonNode postJson(String path, String jsonBody) {
        URI uri = buildUri(path, new LinkedMultiValueMap<>());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "LycanClawBackend/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
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

    private int parseCommentCount(JsonNode node, String path) {
        if (node == null || node.isNull()) return 0;
        if (node.isNumber()) return Math.max(0, node.asInt(0));
        if (node.isArray() && node.size() > 0 && node.get(0).isNumber()) {
            return Math.max(0, node.get(0).asInt(0));
        }

        String noSlash = path.startsWith("/") ? path.substring(1) : path;
        String withSlash = path.startsWith("/") ? path : "/" + path;

        if (node.has(path) && node.get(path).isNumber()) return Math.max(0, node.get(path).asInt(0));
        if (node.has(noSlash) && node.get(noSlash).isNumber()) return Math.max(0, node.get(noSlash).asInt(0));
        if (node.has(withSlash) && node.get(withSlash).isNumber()) return Math.max(0, node.get(withSlash).asInt(0));
        if (node.has("data") && node.get("data").isNumber()) return Math.max(0, node.get("data").asInt(0));

        return 0;
    }

    private int parseInteger(JsonNode node) {
        if (node == null || node.isNull()) return 0;
        if (node.isNumber()) return Math.max(0, node.asInt(0));
        if (node.has("data") && node.get("data").isNumber()) return Math.max(0, node.get("data").asInt(0));
        return 0;
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
