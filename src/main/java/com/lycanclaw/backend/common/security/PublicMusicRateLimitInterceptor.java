package com.lycanclaw.backend.common.security;

import com.lycanclaw.backend.common.api.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description 公共音乐接口限流拦截器
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Component
public class PublicMusicRateLimitInterceptor implements HandlerInterceptor {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Value("${lycan.security.music-rate-limit-per-minute}")
    private int rateLimitPerMinute;

    private final Map<String, ArrayDeque<Long>> buckets = new ConcurrentHashMap<>();

    /**
     * 对公开音乐接口做分钟级限流，防止被批量刷接口。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = resolveClientIp(request) + ":" + request.getRequestURI();
        long now = Instant.now().toEpochMilli();

        ArrayDeque<Long> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            long windowStart = now - 60_000L;
            while (!bucket.isEmpty() && bucket.peekFirst() < windowStart) {
                bucket.pollFirst();
            }

            if (bucket.size() >= Math.max(1, rateLimitPerMinute)) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"success\":false,\"data\":null,\"error\":{\"code\":\""
                                + ErrorCodes.MUSIC_RATE_LIMITED
                                + "\",\"message\":\"音乐接口请求过于频繁，请稍后再试\"}}"
                );
                return false;
            }
            bucket.addLast(now);
        }
        return true;
    }

    /**
     * 反向代理场景优先使用 X-Forwarded-For 首 IP，避免所有请求被算成同一个反代地址。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",")[0].trim();
            if (!first.isBlank()) {
                return normalizeIp(first);
            }
        }
        return normalizeIp(request.getRemoteAddr());
    }

    private String normalizeIp(String value) {
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
