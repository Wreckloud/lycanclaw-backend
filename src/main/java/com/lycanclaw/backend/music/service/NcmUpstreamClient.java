package com.lycanclaw.backend.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.music.config.MusicUpstreamProperties;
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
import java.util.Map;

@Component
public class NcmUpstreamClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MusicUpstreamProperties properties;

    public NcmUpstreamClient(ObjectMapper objectMapper, MusicUpstreamProperties properties) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public JsonNode get(String path, Map<String, String> queryParams) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(normalizeBaseUrl(properties.getBaseUrl()) + path)
                .queryParams(toMultiValueMap(queryParams))
                .build(true)
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "LycanClawBackend/1.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("上游音乐服务请求失败，状态码: " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("解析上游音乐服务返回内容失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求上游音乐服务时线程被中断", e);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("未配置音乐上游服务地址 lycan.music.upstream.base-url");
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private MultiValueMap<String, String> toMultiValueMap(Map<String, String> values) {
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                queryParams.add(entry.getKey(), entry.getValue());
            }
        }
        return queryParams;
    }
}
