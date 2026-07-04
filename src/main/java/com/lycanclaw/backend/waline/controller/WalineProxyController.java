package com.lycanclaw.backend.waline.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.common.security.ClientIpResolver;
import com.lycanclaw.backend.common.security.InMemorySlidingWindowRateLimiter;
import com.lycanclaw.backend.waline.config.WalineProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Waline 同源代理。
 * 让管理端可以通过 /waline/* 访问本地 Waline，避免登录窗口、Cookie 与 CORS 被拆到多个来源。
 * @author Wreckloud
 * @since 2026-06-03
 */
@RestController
@Tag(name = "Waline 代理", description = "评论服务页面、API 与静态资源同源代理")
public class WalineProxyController {

    private static final Logger log = LoggerFactory.getLogger(WalineProxyController.class);
    private static final String PROXY_PREFIX = "/waline";
    private static final Set<String> ALLOWED_OAUTH_PROVIDERS = Set.of("github", "qq");
    private static final Pattern OAUTH_SERVICES_PATTERN = Pattern.compile(
            "window\\.oauthServices\\s*=\\s*(\\[[\\s\\S]*?])\\s*;",
            Pattern.MULTILINE
    );
    private static final List<String> SKIPPED_REQUEST_HEADERS = List.of(
            "host",
            "connection",
            "transfer-encoding",
            "content-length",
            "accept-encoding",
            "forwarded",
            "x-forwarded-for",
            "x-forwarded-host",
            "x-forwarded-proto",
            "x-real-ip",
            "remote-host"
    );
    private static final List<String> SKIPPED_RESPONSE_HEADERS = List.of(
            "connection",
            "transfer-encoding",
            "content-length",
            "access-control-allow-origin",
            "access-control-allow-methods",
            "access-control-allow-headers",
            "access-control-allow-credentials",
            "access-control-max-age"
    );
    private final WalineProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final InMemorySlidingWindowRateLimiter rateLimiter;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${lycan.security.comment-rate-limit-per-minute:1}")
    private int commentRateLimitPerMinute;

    public WalineProxyController(
            WalineProperties properties,
            ClientIpResolver clientIpResolver,
            InMemorySlidingWindowRateLimiter rateLimiter,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiter = rateLimiter;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 代理 Waline 页面、API 与静态资源。
     */
    @Operation(summary = "代理 Waline 页面和接口")
    @RequestMapping("/waline/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readAllBytes();
            ResponseEntity<byte[]> rejected = rejectUnsafePublicWrite(request, body);
            if (rejected != null) {
                return rejected;
            }
            HttpResponse<byte[]> response = httpClient.send(
                    buildProxyRequest(request, body),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            return buildResponse(request, response);
        } catch (IOException ex) {
            log.warn("Waline proxy request failed: {}", request.getRequestURI(), ex);
            return textResponse(HttpStatus.BAD_GATEWAY, "Waline 代理请求失败");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Waline proxy request interrupted: {}", request.getRequestURI(), ex);
            return textResponse(HttpStatus.BAD_GATEWAY, "Waline 代理请求被中断");
        }
    }

    private HttpRequest buildProxyRequest(HttpServletRequest request, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(targetUri(request))
                .timeout(Duration.ofSeconds(20));

        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (shouldCopyRequestHeader(name)) {
                request.getHeaders(name).asIterator().forEachRemaining(value -> builder.header(name, value));
            }
        });
        appendProxySourceHeaders(builder, request);

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
        return filterOauthServices(rewritten).getBytes(StandardCharsets.UTF_8);
    }

    private String filterOauthServices(String html) {
        Matcher matcher = OAUTH_SERVICES_PATTERN.matcher(html);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            try {
                List<Map<String, Object>> services = objectMapper.readValue(
                        matcher.group(1),
                        new TypeReference<>() {
                        }
                );
                List<Map<String, Object>> filtered = services.stream()
                        .filter(service -> isAllowedOauthProvider(service.get("name")))
                        .toList();
                String replacement = "window.oauthServices = " + objectMapper.writeValueAsString(filtered) + ";";
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            } catch (IOException ex) {
                return html;
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isAllowedOauthProvider(Object rawName) {
        if (rawName == null) {
            return false;
        }
        return ALLOWED_OAUTH_PROVIDERS.contains(rawName.toString().toLowerCase(Locale.ROOT));
    }

    private ResponseEntity<byte[]> rejectUnsafePublicWrite(HttpServletRequest request, byte[] body) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = proxyRequestPath(request);
        if ((PROXY_PREFIX + "/api/article").equals(path)) {
            return textResponse(HttpStatus.FORBIDDEN, "阅读量只能通过博客统计接口更新");
        }
        if (!(PROXY_PREFIX + "/api/comment").equals(path)) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(body);
            if (payload == null || !payload.isObject() || payload.path("nick").asText("").isBlank()) {
                return textResponse(HttpStatus.BAD_REQUEST, "评论称谓不能为空");
            }
            String clientIp = clientIpResolver.resolve(request);
            if (!rateLimiter.allow("waline-comment:" + clientIp, commentRateLimitPerMinute)) {
                return textResponse(HttpStatus.TOO_MANY_REQUESTS, "评论过于频繁，请稍后再试");
            }
            return null;
        } catch (IOException ex) {
            return textResponse(HttpStatus.BAD_REQUEST, "评论请求格式错误");
        }
    }

    /**
     * Waline 管理前端偶尔会跳转到裸 /ui 路径，这里统一收回到同源代理前缀。
     */
    @Operation(summary = "重定向 Waline UI 裸路径")
    @RequestMapping({"/ui", "/ui/**"})
    public ResponseEntity<Void> redirectBareUiPath(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, walineUiRedirectLocation(request))
                .build();
    }

    private String proxyRequestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String path = request.getRequestURI().substring(contextPath.length());
        return path.length() > PROXY_PREFIX.length() && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
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

    private String walineUiRedirectLocation(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String path = request.getRequestURI().substring(contextPath.length());
        String query = request.getQueryString();
        return contextPath + PROXY_PREFIX + path + (query == null || query.isBlank() ? "" : "?" + query);
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
        return externalSiteOrigin(request) + PROXY_PREFIX;
    }

    private String externalSiteOrigin(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port) + contextPath;
    }

    private void appendProxySourceHeaders(HttpRequest.Builder builder, HttpServletRequest request) {
        String origin = externalSiteOrigin(request);
        String clientIp = clientIpResolver.resolve(request);
        builder.header("X-Real-IP", clientIp);
        builder.header("X-Forwarded-For", clientIp);
        builder.header("X-Forwarded-Host", forwardedHost(request));
        builder.header("X-Forwarded-Proto", forwardedProto(request));
        // Waline 官方 Nginx 示例会透传 REMOTE-HOST，补齐后避免它只记录后端容器内网 IP。
        builder.header("REMOTE-HOST", clientIp);
        if (request.getHeader(HttpHeaders.ORIGIN) == null) {
            builder.header(HttpHeaders.ORIGIN, origin);
        }
        if (request.getHeader(HttpHeaders.REFERER) == null) {
            builder.header(HttpHeaders.REFERER, origin + "/");
        }
    }

    private String forwardedHost(HttpServletRequest request) {
        String host = request.getHeader(HttpHeaders.HOST);
        if (host != null && !host.isBlank()) {
            return host.trim();
        }
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && port == 443);
        return request.getServerName() + (defaultPort ? "" : ":" + port);
    }

    private String forwardedProto(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto == null || proto.isBlank() ? request.getScheme() : proto.split(",")[0].trim();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

