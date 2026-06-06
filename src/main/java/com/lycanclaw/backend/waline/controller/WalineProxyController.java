package com.lycanclaw.backend.waline.controller;

import com.lycanclaw.backend.waline.config.WalineProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Waline 同源代理。
 * 让管理端可以通过 /waline/* 访问本地 Waline，避免登录窗口、Cookie 与 CORS 被拆到多个来源。
 * @author Wreckloud
 * @since 2026-06-03
 */
@RestController
public class WalineProxyController {

    private static final String PROXY_PREFIX = "/waline";
    private static final List<String> SKIPPED_REQUEST_HEADERS = List.of(
            "host",
            "connection",
            "transfer-encoding",
            "content-length",
            "accept-encoding"
    );
    private static final List<String> SKIPPED_RESPONSE_HEADERS = List.of(
            "connection",
            "transfer-encoding",
            "content-length"
    );
    private final WalineProperties properties;
    private final HttpClient httpClient;

    public WalineProxyController(WalineProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    /**
     * 代理 Waline 页面、API 与静态资源。
     */
    @RequestMapping("/waline/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    buildProxyRequest(request),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            return buildResponse(request, response);
        } catch (IOException ex) {
            return textResponse(HttpStatus.BAD_GATEWAY, "Waline 代理请求失败: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return textResponse(HttpStatus.BAD_GATEWAY, "Waline 代理请求被中断");
        }
    }

    private HttpRequest buildProxyRequest(HttpServletRequest request) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(targetUri(request))
                .timeout(Duration.ofSeconds(20));

        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (shouldCopyRequestHeader(name)) {
                request.getHeaders(name).asIterator().forEachRemaining(value -> builder.header(name, value));
            }
        });

        byte[] body = request.getInputStream().readAllBytes();
        HttpRequest.BodyPublisher publisher = body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        return builder.method(request.getMethod(), publisher).build();
    }

    private ResponseEntity<byte[]> buildResponse(HttpServletRequest request, HttpResponse<byte[]> response) {
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
            if (shouldCopyResponseHeader(entry.getKey())) {
                headers.put(entry.getKey(), rewriteResponseHeader(request, entry.getKey(), entry.getValue()));
            }
        }

        byte[] body = response.body();
        if (isHtml(headers)) {
            body = rewriteWalineHtml(body, request);
        }
        return ResponseEntity.status(response.statusCode()).headers(headers).body(body);
    }

    private byte[] rewriteWalineHtml(byte[] body, HttpServletRequest request) {
        String html = new String(body, StandardCharsets.UTF_8);
        String baseUrl = normalizeBaseUrl();
        String proxyBase = externalProxyBase(request);
        String rewritten = html
                .replace(baseUrl + "/api/", PROXY_PREFIX + "/api/")
                .replace(baseUrl + "/api", PROXY_PREFIX + "/api")
                .replace(baseUrl + "/ui/", proxyBase + "/ui/")
                .replace(baseUrl + "/ui", proxyBase + "/ui");
        return rewritten.getBytes(StandardCharsets.UTF_8);
    }

    private List<String> rewriteResponseHeader(HttpServletRequest request, String name, List<String> values) {
        if (!HttpHeaders.LOCATION.equalsIgnoreCase(name)) {
            return values;
        }
        return values.stream().map(value -> rewriteLocation(request, value)).toList();
    }

    private String rewriteLocation(HttpServletRequest request, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String baseUrl = normalizeBaseUrl();
        String proxyBase = externalProxyBase(request);
        String rewritten = value
                .replace(baseUrl + "/api/", proxyBase + "/api/")
                .replace(baseUrl + "/api", proxyBase + "/api")
                .replace(baseUrl + "/ui/", proxyBase + "/ui/")
                .replace(baseUrl + "/ui", proxyBase + "/ui");

        rewritten = rewritten
                .replace(urlEncode(baseUrl + "/api/"), urlEncode(proxyBase + "/api/"))
                .replace(urlEncode(baseUrl + "/api"), urlEncode(proxyBase + "/api"))
                .replace(urlEncode(baseUrl + "/ui/"), urlEncode(proxyBase + "/ui/"))
                .replace(urlEncode(baseUrl + "/ui"), urlEncode(proxyBase + "/ui"));

        if (rewritten.startsWith("/api/") || rewritten.equals("/api")) {
            return PROXY_PREFIX + rewritten;
        }
        if (rewritten.startsWith("/ui/") || rewritten.equals("/ui")) {
            return PROXY_PREFIX + rewritten;
        }
        return rewritten;
    }

    private URI targetUri(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String path = requestUri.substring(contextPath.length());
        String targetPath = path.length() <= PROXY_PREFIX.length() ? "/" : path.substring(PROXY_PREFIX.length());
        String query = request.getQueryString();
        return URI.create(normalizeBaseUrl() + targetPath + (query == null || query.isBlank() ? "" : "?" + query));
    }

    private boolean isHtml(HttpHeaders headers) {
        return headers.getOrDefault(HttpHeaders.CONTENT_TYPE, List.of()).stream()
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("text/html"));
    }

    private boolean shouldCopyRequestHeader(String name) {
        return !SKIPPED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean shouldCopyResponseHeader(String name) {
        return !SKIPPED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }

    private ResponseEntity<byte[]> textResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
                .body(message.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("未配置 Waline 服务地址 lycan.waline.base-url");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String externalProxyBase(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port) + contextPath + PROXY_PREFIX;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
