package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.common.path.WebPathNormalizer;
import org.springframework.stereotype.Component;

/**
 * 统计路径策略。
 * 用于统一判断哪些前台路径需要记录访问统计，并推断页面类型。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Component
public class AnalyticsPathPolicy {

    public String normalizePath(String value) {
        return WebPathNormalizer.normalize(value);
    }

    public boolean isTrackable(String path) {
        String normalized = normalizePath(path);
        if (normalized.startsWith("/admin") || normalized.startsWith("/api") || normalized.startsWith("/assets")) {
            return false;
        }
        return isHome(normalized) || isCorePage(normalized) || isArticle(normalized);
    }

    public String inferPageType(String path, String requestedType) {
        String normalized = normalizePath(path);
        if (requestedType != null && !requestedType.isBlank()) {
            return requestedType.trim().toLowerCase();
        }
        if (isHome(normalized)) {
            return "home";
        }
        if (isArticle(normalized)) {
            return "article";
        }
        return "core";
    }

    public boolean isArticle(String path) {
        String normalized = normalizePath(path);
        if (!(normalized.startsWith("/thoughts/") || normalized.startsWith("/knowledge/"))) {
            return false;
        }
        return normalized.endsWith(".html") && !normalized.endsWith("/index.html") && !normalized.endsWith("/tags.html");
    }

    private boolean isHome(String path) {
        return "/".equals(path) || "/index.html".equals(path);
    }

    private boolean isCorePage(String path) {
        return path.equals("/about.html")
                || path.equals("/about")
                || path.equals("/thoughts/")
                || path.equals("/thoughts/index.html")
                || path.equals("/knowledge/")
                || path.equals("/knowledge/index.html")
                || path.equals("/projects/")
                || path.equals("/projects/index.html");
    }
}
