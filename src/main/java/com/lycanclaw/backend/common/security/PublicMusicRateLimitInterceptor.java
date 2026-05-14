package com.lycanclaw.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PublicMusicRateLimitInterceptor implements HandlerInterceptor {

    @Value("${lycan.security.music-rate-limit-per-minute:120}")
    private int rateLimitPerMinute;

    private final Map<String, ArrayDeque<Long>> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = request.getRemoteAddr() + ":" + request.getRequestURI();
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
                response.getWriter().write("{\"success\":false,\"data\":null,\"error\":{\"code\":\"请求过快\",\"message\":\"音乐接口请求过于频繁，请稍后再试\"}}");
                return false;
            }
            bucket.addLast(now);
        }
        return true;
    }
}
