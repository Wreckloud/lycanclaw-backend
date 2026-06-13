package com.lycanclaw.backend.waline.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.waline.config.WalineProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class WalineProxyController {

    private static final Logger log = LoggerFactory.getLogger(WalineProxyController.class);
    private static final String PROXY_PREFIX = "/waline";
    private static final Set<String> ALLOWED_OAUTH_PROVIDERS = Set.of("github", "qq");
    private static final Pattern OAUTH_SERVICES_PATTERN = Pattern.compile(
            "window\\.oauthServices\\s*=\\s*(\\[[\\s\\S]*?])\\s*;",
            Pattern.MULTILINE
    );
    private static final String AUTH_PAGE_STYLE = """
            <style id="lycan-waline-auth-theme">
              :root {
                color-scheme: dark;
                --lycan-bg: #0d1119;
                --lycan-surface: #151a24;
                --lycan-surface-soft: #10151e;
                --lycan-text: #eef3ff;
                --lycan-muted: #9ca8bb;
                --lycan-accent: #19c56f;
              }

              html,
              body,
              body .waline-root {
                min-height: 100%;
                background: var(--lycan-bg) !important;
                color: var(--lycan-text) !important;
              }

              body {
                margin: 0 !important;
                font-family: "Microsoft YaHei", "Segoe UI", sans-serif !important;
              }

              body .typecho-head-nav {
                position: static !important;
                width: min(380px, calc(100% - 32px)) !important;
                margin: min(18vh, 110px) auto 0 !important;
                padding: 0 !important;
                background: transparent !important;
                box-shadow: none !important;
              }

              body .waline-header {
                min-height: 56px !important;
                padding: 12px 16px !important;
                background: var(--lycan-surface) !important;
                border: 0 !important;
                border-radius: 0 !important;
                box-shadow: none !important;
              }

              body .waline-brand-link {
                gap: 10px !important;
                color: var(--lycan-text) !important;
                text-decoration: none !important;
              }

              body .waline-brand-mark {
                width: 30px !important;
                height: 30px !important;
                background: transparent url("/admin/logo.png") center / contain no-repeat !important;
                font-size: 0 !important;
              }

              body .waline-brand-copy strong {
                color: var(--lycan-text) !important;
                font-size: 15px !important;
                font-weight: 800 !important;
              }

              body .language-select {
                display: none !important;
              }

              body .typecho-login-wrap {
                width: min(380px, calc(100% - 32px)) !important;
                margin: 8px auto 32px !important;
                padding: 0 !important;
              }

              body .typecho-login {
                width: 100% !important;
                margin: 0 !important;
                padding: 20px 16px 18px !important;
                background: var(--lycan-surface) !important;
                border: 0 !important;
                border-radius: 0 !important;
                box-shadow: none !important;
                text-align: center !important;
              }

              body .typecho-login::before {
                content: "使用 QQ 或 GitHub 登录";
                display: block;
                margin-bottom: 16px;
                color: var(--lycan-muted);
                font-size: 13px;
              }

              body .typecho-login form,
              body .typecho-login .more-link {
                display: none !important;
              }

              body .social-accounts {
                display: flex !important;
                justify-content: center !important;
                gap: 12px !important;
                margin: 0 !important;
              }

              body .social-accounts a {
                width: 42px !important;
                height: 42px !important;
                display: grid !important;
                place-items: center !important;
                background: var(--lycan-surface-soft) !important;
                border: 0 !important;
                border-radius: 0 !important;
                box-shadow: none !important;
              }

              body .social-accounts a:hover,
              body .social-accounts a:focus {
                color: var(--lycan-accent) !important;
                background: #1b222f !important;
                transform: none !important;
              }

              body .social-accounts .social-icon {
                width: 26px !important;
                height: 26px !important;
              }

              body .message.popup {
                color: var(--lycan-text) !important;
                background: #1b222f !important;
                border: 0 !important;
                border-radius: 0 !important;
                box-shadow: none !important;
              }

              @media (max-width: 520px) {
                body .typecho-head-nav {
                  margin-top: 32px !important;
                }

                body .waline-header,
                body .typecho-login {
                  padding: 16px !important;
                }
              }
            </style>
            """;
    private static final String OAUTH_FILTER_STYLE = """
            <style id="lycan-waline-oauth-filter-style">
              a[href*="type=weibo"],
              a[href*="type=twitter"],
              a[href*="type=facebook"],
              a[href*="type%3Dweibo"],
              a[href*="type%3Dtwitter"],
              a[href*="type%3Dfacebook"] {
                display: none !important;
              }
            </style>
            """;
    private static final String OAUTH_FILTER_SCRIPT = """
            <script id="lycan-waline-oauth-filter">
              (() => {
                const allowed = new Set(['github', 'qq']);
                const getType = (href) => {
                  if (!href) return '';
                  try {
                    const url = new URL(href, window.location.origin);
                    return (url.searchParams.get('type') || '').toLowerCase();
                  } catch {
                    try {
                      const decoded = decodeURIComponent(href);
                      const match = decoded.match(/[?&]type=([^&#]+)/);
                      return match ? match[1].toLowerCase() : '';
                    } catch {
                      return '';
                    }
                  }
                };
                const prune = () => {
                  if (Array.isArray(window.oauthServices)) {
                    window.oauthServices = window.oauthServices.filter(service =>
                      allowed.has(String(service?.name || '').toLowerCase())
                    );
                  }
                  document.querySelectorAll('a[href*="oauth"]').forEach(link => {
                    const type = getType(link.getAttribute('href') || '');
                    if (type && !allowed.has(type)) {
                      link.remove();
                    }
                  });
                };
                const install = () => {
                  prune();
                  new MutationObserver(prune).observe(document.documentElement, {
                    childList: true,
                    subtree: true
                  });
                };
                if (document.readyState === 'loading') {
                  document.addEventListener('DOMContentLoaded', install, { once: true });
                } else {
                  install();
                }
              })();
            </script>
            """;
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
    private final ObjectMapper objectMapper;

    public WalineProxyController(WalineProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.objectMapper = objectMapper;
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
            log.warn("Waline proxy request failed: {}", request.getRequestURI(), ex);
            return textResponse(HttpStatus.BAD_GATEWAY, "Waline 代理请求失败");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Waline proxy request interrupted: {}", request.getRequestURI(), ex);
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
        rewritten = filterOauthServices(rewritten);
        if (isWalineUiPage(request)) {
            rewritten = injectBeforeClosingTag(rewritten, "</head>", OAUTH_FILTER_STYLE + OAUTH_FILTER_SCRIPT);
        }
        if (isWalineAuthPage(request)) {
            rewritten = injectBeforeClosingTag(rewritten, "</head>", AUTH_PAGE_STYLE);
        }
        return rewritten.getBytes(StandardCharsets.UTF_8);
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

    private boolean isWalineUiPage(HttpServletRequest request) {
        return request.getRequestURI().contains(PROXY_PREFIX + "/ui/");
    }

    private boolean isWalineAuthPage(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri.endsWith("/ui/login")
                || requestUri.endsWith("/ui/forgot")
                || requestUri.endsWith("/ui/register");
    }

    private String injectBeforeClosingTag(String html, String closingTag, String content) {
        int index = html.toLowerCase(Locale.ROOT).lastIndexOf(closingTag);
        if (index < 0) {
            return html;
        }
        return html.substring(0, index) + content + html.substring(index);
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
