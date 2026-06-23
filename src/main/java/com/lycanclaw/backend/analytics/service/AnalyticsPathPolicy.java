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

    private static final int MAX_PATH_LENGTH = 512;
    private static final String[] STATIC_SUFFIXES = {
            ".js", ".css", ".map", ".json", ".xml", ".txt", ".ico",
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".avif", ".bmp",
            ".wasm", ".woff", ".woff2", ".ttf", ".otf", ".eot",
            ".mp3", ".wav", ".ogg", ".flac", ".m4a", ".mp4", ".webm",
            ".pdf", ".zip", ".gz"
    };

    public String normalizePath(String value) {
        return WebPathNormalizer.normalize(value);
    }

    public boolean isTrackable(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = normalizePath(path);
        if (isInvalid(normalized)
                || isReservedPrefix(normalized, "/admin")
                || isReservedPrefix(normalized, "/api")
                || isReservedPrefix(normalized, "/assets")
                || isReservedPrefix(normalized, "/.vitepress")
                || hasStaticSuffix(normalized)) {
            return false;
        }
        return true;
    }

    public String inferPageType(String path) {
        String normalized = normalizePath(path);
        if (isHome(normalized)) {
            return "home";
        }
        if (isArticle(normalized)) {
            return "article";
        }
        return "page";
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

    private boolean isInvalid(String path) {
        if (path.isBlank() || path.length() > MAX_PATH_LENGTH || path.indexOf('\\') >= 0 || path.contains("//")) {
            return true;
        }
        for (int index = 0; index < path.length(); index++) {
            if (Character.isISOControl(path.charAt(index))) {
                return true;
            }
        }
        for (String segment : path.split("/")) {
            if ("..".equals(segment) || ".".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReservedPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private boolean hasStaticSuffix(String path) {
        String lowerCasePath = path.toLowerCase();
        for (String suffix : STATIC_SUFFIXES) {
            if (lowerCasePath.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
