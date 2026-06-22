package com.lycanclaw.backend.common.path;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

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
        }
        return normalized.isBlank() ? "/" : normalized;
    }
}
