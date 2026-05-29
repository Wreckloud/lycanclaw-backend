package com.lycanclaw.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * ClientIpResolver：
 * 负责ClientIpResolver相关的安全控制。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Value("${lycan.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    /**
     * trust-forwarded-headers=true 时优先取 X-Forwarded-For 首 IP；
     * 否则退回 remoteAddr。
     */
    public String resolve(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String first = forwardedFor.split(",")[0].trim();
                if (!first.isBlank()) {
                    return normalize(first);
                }
            }
        }
        return normalize(request.getRemoteAddr());
    }

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.startsWith("::ffff:")) {
            normalized = normalized.substring("::ffff:".length());
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
