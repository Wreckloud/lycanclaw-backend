package com.lycanclaw.backend.waline.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.waline.config.WalineProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Waline 代理", description = "评论服务页面、API 与静态资源同源代理")
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
                width: min(760px, calc(100% - 32px)) !important;
                margin: min(18vh, 110px) auto 0 !important;
                padding: 0 !important;
                background: transparent !important;
                box-shadow: none !important;
              }

              body:has(.typecho-login) .typecho-head-nav {
                display: block !important;
                margin: min(16vh, 96px) auto 16px !important;
              }

              body .waline-header {
                min-height: 56px !important;
                padding: 12px 16px !important;
                background: var(--lycan-surface) !important;
                border: 0 !important;
                border-radius: 3px !important;
                box-shadow: none !important;
              }

              body:has(.typecho-login) .waline-header {
                min-height: auto !important;
                padding: 0 !important;
                justify-content: center !important;
                background: transparent !important;
              }

              body .waline-brand-link {
                gap: 10px !important;
                color: var(--lycan-text) !important;
                text-decoration: none !important;
              }

              body:has(.typecho-login) .waline-brand-link {
                pointer-events: none !important;
                cursor: default !important;
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

              body:has(.typecho-login) .waline-brand-copy strong {
                font-size: 20px !important;
              }

              body .language-select {
                display: none !important;
              }

              body .typecho-login-wrap {
                width: min(380px, calc(100% - 32px)) !important;
                margin: 0 auto 32px !important;
                padding: 0 !important;
              }

              body .typecho-login {
                width: 100% !important;
                margin: 0 !important;
                padding: 20px 16px 18px !important;
                background: var(--lycan-surface) !important;
                border: 0 !important;
                border-radius: 3px !important;
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
                background: transparent !important;
                border: 0 !important;
                border-radius: 3px !important;
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
                border-radius: 3px !important;
                box-shadow: none !important;
              }

              body .typecho-page-main,
              body .typecho-page-container,
              body .typecho-profile,
              body .typecho-option,
              body .typecho-list-table,
              body .typecho-table-wrap {
                width: min(760px, calc(100% - 32px)) !important;
                margin-left: auto !important;
                margin-right: auto !important;
                color: var(--lycan-text) !important;
                background: var(--lycan-surface) !important;
                border: 0 !important;
                border-radius: 0 !important;
                box-shadow: none !important;
              }

              body .typecho-page-main,
              body .typecho-page-container,
              body .typecho-profile {
                margin-top: 12px !important;
                padding: 18px !important;
              }

              body input,
              body select,
              body textarea {
                color: var(--lycan-text) !important;
                background: var(--lycan-surface-soft) !important;
                border: 0 !important;
                border-radius: 3px !important;
                box-shadow: none !important;
                outline: 0 !important;
              }

              body input:focus,
              body select:focus,
              body textarea:focus {
                background: #202837 !important;
              }

              body button,
              body .btn,
              body input[type="submit"] {
                color: var(--lycan-text) !important;
                background: #1b222f !important;
                border: 0 !important;
                border-radius: 3px !important;
                box-shadow: none !important;
              }

              body button:hover,
              body .btn:hover,
              body input[type="submit"]:hover {
                color: #d9ffe8 !important;
                background: rgba(25, 197, 111, .18) !important;
                transform: none !important;
              }

              body table,
              body th,
              body td {
                color: var(--lycan-text) !important;
                background: transparent !important;
                border-color: #1b222f !important;
              }

              body a {
                color: var(--lycan-text) !important;
              }

              body a:hover {
                color: var(--lycan-accent) !important;
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
    private static final String PROFILE_PAGE_STYLE = """
            <style id="lycan-waline-profile-theme">
              body * {
                box-shadow: none !important;
                border-radius: 0 !important;
              }

              body .el-alert,
              body .typecho-alert,
              body .notice,
              body .update-message,
              body [class*="version"],
              body [class*="upgrade"] {
                display: none !important;
              }

              body .typecho-head-nav {
                width: min(960px, calc(100% - 48px)) !important;
                margin: 28px auto 0 !important;
              }

              body:has(.typecho-login) .typecho-head-nav {
                width: min(380px, calc(100% - 32px)) !important;
                margin-top: min(14vh, 96px) !important;
              }

              body .waline-header {
                min-height: 64px !important;
                padding: 0 18px !important;
                display: flex !important;
                align-items: center !important;
                justify-content: flex-end !important;
                gap: 10px !important;
                background: transparent !important;
              }

              body .waline-header .waline-brand-link,
              body .waline-header .waline-brand-copy,
              body .waline-header .waline-brand-mark,
              body .waline-header nav,
              body .waline-header ul,
              body .waline-header li {
                display: none !important;
              }

              body .waline-header a,
              body .waline-header button,
              body .waline-header .btn {
                min-height: 32px !important;
                padding: 0 12px !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                color: #eef3ff !important;
                background: transparent !important;
                border: 1px solid #202838 !important;
                border-radius: 3px !important;
                text-decoration: none !important;
                font-weight: 700 !important;
              }

              body .waline-header a:hover,
              body .waline-header button:hover,
              body .waline-header .btn:hover {
                color: #19c56f !important;
                background: #121b28 !important;
              }

              body .waline-brand-link {
                margin-right: 10px !important;
                background: transparent !important;
                border: 0 !important;
                padding: 0 !important;
              }

              body .typecho-page-main,
              body .typecho-page-container {
                width: min(960px, calc(100% - 48px)) !important;
                margin: 28px auto 48px !important;
                padding: 0 !important;
                color: #eef3ff !important;
                background: transparent !important;
                border: 0 !important;
              }

              body .typecho-profile,
              body .typecho-option,
              body .typecho-table-wrap,
              body .typecho-list-table {
                width: 100% !important;
                box-sizing: border-box !important;
                margin: 0 0 14px !important;
                color: #eef3ff !important;
                background: #151a24 !important;
                border: 1px solid #202838 !important;
                border-radius: 3px !important;
              }

              body .typecho-profile {
                min-height: 0 !important;
                display: grid !important;
                grid-template-columns: 180px minmax(0, 1fr) !important;
                gap: 22px !important;
                align-items: start !important;
                padding: 22px !important;
              }

              body .typecho-profile > :first-child {
                align-self: stretch !important;
                display: grid !important;
                place-items: center !important;
                padding: 18px !important;
                background: #10151e !important;
                border: 1px solid #202838 !important;
                border-radius: 3px !important;
              }

              body .typecho-profile > :not(:first-child) {
                min-width: 0 !important;
              }

              body .typecho-option,
              body .typecho-table-wrap {
                padding: 18px !important;
              }

              body .typecho-profile h1,
              body .typecho-profile h2,
              body .typecho-profile h3,
              body .typecho-option h1,
              body .typecho-option h2,
              body .typecho-option h3,
              body .typecho-page-main h1,
              body .typecho-page-main h2,
              body .typecho-page-main h3 {
                margin: 0 0 12px !important;
                color: #eef3ff !important;
                font-size: 20px !important;
                line-height: 1.35 !important;
                font-weight: 900 !important;
              }

              body .typecho-page-main h1::after,
              body .typecho-page-main h2::after,
              body .typecho-page-main h3::after {
                content: "";
                display: block;
                width: 44px;
                height: 3px;
                margin-top: 10px;
                background: #19c56f;
              }

              body .typecho-option-title,
              body .typecho-label,
              body label {
                display: block !important;
                margin: 0 0 7px !important;
                color: #d7dfeb !important;
                font-size: 13px !important;
                font-weight: 800 !important;
              }

              body form,
              body .typecho-option-content {
                display: grid !important;
                gap: 12px !important;
              }

              body input,
              body select,
              body textarea {
                min-height: 38px !important;
                width: 100% !important;
                box-sizing: border-box !important;
                padding: 8px 10px !important;
                color: #eef3ff !important;
                background: #10151e !important;
                border: 1px solid #202838 !important;
                border-radius: 3px !important;
                outline: 0 !important;
              }

              body input:focus,
              body select:focus,
              body textarea:focus {
                border-color: rgba(25, 197, 111, .58) !important;
                background: #111925 !important;
              }

              body .typecho-option p,
              body .typecho-profile p,
              body .typecho-page-main p,
              body .typecho-description,
              body .description,
              body small {
                color: #9ca8bb !important;
                font-size: 12px !important;
                line-height: 1.7 !important;
              }

              body .typecho-option-submit,
              body .submit,
              body .actions {
                display: flex !important;
                flex-wrap: wrap !important;
                gap: 10px !important;
                align-items: center !important;
              }

              body input[type="submit"],
              body button[type="submit"],
              body .primary,
              body .btn-primary {
                width: auto !important;
                min-width: 96px !important;
                color: #06100b !important;
                background: #19c56f !important;
                border-color: #19c56f !important;
                font-weight: 900 !important;
              }

              body input[type="submit"]:hover,
              body button[type="submit"]:hover,
              body .primary:hover,
              body .btn-primary:hover {
                color: #06100b !important;
                background: #29d77f !important;
              }

              body table {
                width: 100% !important;
                border-collapse: collapse !important;
              }

              body table,
              body th,
              body td {
                color: #eef3ff !important;
                background: transparent !important;
                border-color: #202838 !important;
              }

              body th {
                color: #9ca8bb !important;
                font-size: 12px !important;
                font-weight: 800 !important;
              }

              body td,
              body th {
                padding: 10px 8px !important;
              }

              body img,
              body .avatar {
                object-fit: cover !important;
                background: #10151e !important;
                border: 1px solid #202838 !important;
                border-radius: 3px !important;
              }

              body .typecho-profile img,
              body .avatar {
                width: 84px !important;
                height: 84px !important;
              }

              @media (max-width: 720px) {
                body .typecho-head-nav,
                body .typecho-page-main,
                body .typecho-page-container {
                  width: min(100% - 28px, 960px) !important;
                }

                body .typecho-profile {
                  grid-template-columns: 1fr !important;
                  gap: 14px !important;
                  padding: 16px !important;
                }
              }
            </style>
            """;
    private static final String UI_CLEANUP_SCRIPT = """
            <script id="lycan-waline-ui-cleanup">
              (() => {
                const unwantedText = [
                  /新版本\\s*@waline\\/vercel/i,
                  /请尽快升级/i,
                  /了解如何升级/i,
                  /Leancloud\\s*即将停止对外服务/i,
                  /LeanCloud\\s*即将停止对外服务/i,
                  /迁移你的评论数据/i,
                  /两步验证/i,
                  /双重验证/i,
                  /2FA/i,
                  /two\\s*factor/i
                ];
                const allowedOauth = new Set(['github', 'qq']);
                const candidateContainer = (node) => {
                  if (!node || node.nodeType !== Node.TEXT_NODE) return null;
                  let el = node.parentElement;
                  while (el && el !== document.body) {
                    const tag = el.tagName ? el.tagName.toLowerCase() : '';
                    const text = (el.textContent || '').trim();
                    if (['p', 'li', 'tr', 'section', 'article'].includes(tag)) return el;
                    if (el.classList && (
                      el.classList.contains('el-alert') ||
                      el.classList.contains('typecho-alert') ||
                      el.classList.contains('notice') ||
                      el.classList.contains('message') ||
                      el.className.toString().includes('alert') ||
                      el.className.toString().includes('notice')
                    )) return el;
                    if (text.length > 20 && text.length < 500 && el.children.length <= 8) return el;
                    el = el.parentElement;
                  }
                  return null;
                };
                const getOauthType = (href) => {
                  if (!href) return '';
                  try {
                    return (new URL(href, location.origin).searchParams.get('type') || '').toLowerCase();
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
                const removeNoise = () => {
                  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
                  const removals = [];
                  while (walker.nextNode()) {
                    const text = (walker.currentNode.nodeValue || '').trim();
                    if (text && unwantedText.some(pattern => pattern.test(text))) {
                      const target = candidateContainer(walker.currentNode);
                      if (target) removals.push(target);
                    }
                  }
                  removals.forEach(el => el.remove());

                  document.querySelectorAll('a[href*="oauth"], a[href*="OAuth"]').forEach(link => {
                    const type = getOauthType(link.getAttribute('href') || '');
                    if (type && !allowedOauth.has(type)) link.remove();
                  });

                  document.querySelectorAll('input, button, a, label, th, td, p, div').forEach(el => {
                    const text = (el.textContent || el.value || '').trim();
                    if (unwantedText.some(pattern => pattern.test(text))) {
                      const target = el.closest('.typecho-option, tr, section, article, p, li, div') || el;
                      if (target && target !== document.body) target.remove();
                    }
                  });

                  document.querySelectorAll('img').forEach(img => {
                    if (!img.getAttribute('src')) img.src = '/admin/default.png';
                    img.addEventListener('error', () => {
                      img.src = '/admin/default.png';
                    }, { once: true });
                  });
                };
                const install = () => {
                  removeNoise();
                  new MutationObserver(removeNoise).observe(document.documentElement, {
                    childList: true,
                    subtree: true,
                    characterData: true
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
            "content-length",
            "access-control-allow-origin",
            "access-control-allow-methods",
            "access-control-allow-headers",
            "access-control-allow-credentials",
            "access-control-max-age"
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
        rewritten = filterOauthServices(rewritten);
        if (isWalineUiPage(request)) {
            rewritten = injectBeforeClosingTag(
                    rewritten,
                    "</head>",
                    AUTH_PAGE_STYLE + PROFILE_PAGE_STYLE + UI_CLEANUP_SCRIPT
            );
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
        if (request.getHeader(HttpHeaders.ORIGIN) == null) {
            builder.header(HttpHeaders.ORIGIN, origin);
        }
        if (request.getHeader(HttpHeaders.REFERER) == null) {
            builder.header(HttpHeaders.REFERER, origin + "/");
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

