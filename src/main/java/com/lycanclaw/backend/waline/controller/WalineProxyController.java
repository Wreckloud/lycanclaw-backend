package com.lycanclaw.backend.waline.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                --lycan-surface-active: #202837;
                --lycan-text: #eef3ff;
                --lycan-muted: #9ca8bb;
                --lycan-faint: #6f7a8d;
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
                width: min(420px, calc(100% - 32px)) !important;
                margin: min(14vh, 88px) auto 0 !important;
                padding: 0 !important;
                background: transparent !important;
                box-shadow: none !important;
              }

              body .waline-header {
                min-height: 68px !important;
                padding: 16px 20px !important;
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
                width: 34px !important;
                height: 34px !important;
                display: grid !important;
                place-items: center !important;
                background: var(--lycan-surface-soft) !important;
                background-image: none !important;
                color: var(--lycan-accent) !important;
                font-size: 0 !important;
              }

              body .waline-brand-mark::after {
                content: "LC";
                font-size: 13px;
                font-weight: 800;
                line-height: 1;
              }

              body .waline-brand-copy strong {
                color: var(--lycan-text) !important;
                font-size: 16px !important;
                font-weight: 800 !important;
              }

              body .language-select select {
                min-height: 38px !important;
                padding: 0 34px 0 12px !important;
                border: 0 !important;
                border-radius: 0 !important;
                outline: 0 !important;
                color: var(--lycan-muted) !important;
                background-color: var(--lycan-surface-soft) !important;
                box-shadow: none !important;
              }

              body .typecho-login-wrap {
                width: min(420px, calc(100% - 32px)) !important;
                margin: 12px auto 32px !important;
                padding: 0 !important;
              }

              body .typecho-login {
                width: 100% !important;
                margin: 0 !important;
                padding: 22px !important;
                background: var(--lycan-surface) !important;
                border: 0 !important;
                border-radius: 0 !important;
                box-shadow: none !important;
              }

              body .typecho-login form > p {
                margin: 0 0 12px !important;
              }

              body .typecho-login input[type="text"],
              body .typecho-login input[type="email"],
              body .typecho-login input[type="password"] {
                width: 100% !important;
                min-height: 42px !important;
                padding: 9px 11px !important;
                border: 0 !important;
                border-radius: 0 !important;
                outline: 0 !important;
                color: var(--lycan-text) !important;
                background: var(--lycan-surface-soft) !important;
                box-shadow: none !important;
              }

              body .typecho-login input::placeholder {
                color: var(--lycan-faint) !important;
              }

              body .typecho-login input:focus {
                background: var(--lycan-surface-active) !important;
                box-shadow: inset 3px 0 0 var(--lycan-accent) !important;
              }

              body .typecho-login .submit {
                margin-top: 16px !important;
              }

              body .typecho-login .btn.primary {
                min-height: 42px !important;
                border: 0 !important;
                border-radius: 0 !important;
                color: #d9ffe8 !important;
                background: rgba(25, 197, 111, .18) !important;
                box-shadow: none !important;
                font-weight: 700 !important;
              }

              body .typecho-login .btn.primary:hover,
              body .typecho-login .btn.primary:focus {
                color: #ecfff4 !important;
                background: rgba(25, 197, 111, .28) !important;
              }

              body .typecho-login label,
              body .typecho-login a {
                color: var(--lycan-muted) !important;
              }

              body .typecho-login a:hover,
              body .typecho-login a:focus {
                color: var(--lycan-text) !important;
              }

              body .typecho-login .checkbox {
                accent-color: var(--lycan-accent);
              }

              body .social-accounts {
                display: flex !important;
                justify-content: center !important;
                gap: 10px !important;
                margin: 20px 0 18px !important;
              }

              body .social-accounts a {
                width: 44px !important;
                height: 44px !important;
                display: grid !important;
                place-items: center !important;
                background: var(--lycan-surface-soft) !important;
                border: 0 !important;
                border-radius: 0 !important;
                box-shadow: none !important;
              }

              body .social-accounts a:hover,
              body .social-accounts a:focus {
                background: var(--lycan-surface-active) !important;
                transform: none !important;
              }

              body .social-accounts .social-icon {
                width: 28px !important;
                height: 28px !important;
              }

              body .more-link {
                margin: 0 !important;
                color: var(--lycan-faint) !important;
                text-align: center !important;
              }

              body .message.popup {
                color: var(--lycan-text) !important;
                background: var(--lycan-surface-active) !important;
                border: 0 !important;
                border-radius: 0 !important;
                box-shadow: none !important;
              }

              @media (max-width: 520px) {
                body .typecho-head-nav {
                  margin-top: 24px !important;
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

    public WalineProxyController(WalineProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.objectMapper = new ObjectMapper();
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
