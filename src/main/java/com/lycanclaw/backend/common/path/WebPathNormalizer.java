package com.lycanclaw.backend.common.path;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Web路径规范化工具。
 * 统一移除查询参数和锚点，并解码URL路径供内容目录和统计服务使用。
 * @author Wreckloud
 * @since 2026-06-24
 */
public final class WebPathNormalizer {

    private WebPathNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        String normalized = value.trim();
        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        try {
            normalized = UriUtils.decode(normalized, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // 无法解码时保留原路径，后续内容目录校验会拒绝未知路径。
        }
        return normalized.isBlank() ? "/" : normalized;
    }
}
