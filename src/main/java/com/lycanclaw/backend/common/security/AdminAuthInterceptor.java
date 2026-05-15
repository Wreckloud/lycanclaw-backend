package com.lycanclaw.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description 管理员鉴权与限流拦截器
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String TOKEN_HEADER = "X-Lycan-Admin-Token";

    @Value("${lycan.security.admin-token}")
    private String adminToken;

    @Value("${lycan.security.auth-rate-limit-per-minute}")
    private int rateLimitPerMinute;

    private final Map<String, ArrayDeque<Long>> rateBuckets = new ConcurrentHashMap<>();

    /**
     * 对 /api/music/auth/* 做双重保护：
     * 1) 校验管理员令牌；
     * 2) 按 IP + URI 做分钟级限流。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader(TOKEN_HEADER);
        if (!Objects.equals(adminToken, token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"data\":null,\"error\":{\"code\":\"未授权\",\"message\":\"管理员令牌无效\"}}");
            return false;
        }

        String key = request.getRemoteAddr() + ":" + request.getRequestURI();
        long now = Instant.now().toEpochMilli();
        ArrayDeque<Long> bucket = rateBuckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            long windowStart = now - 60_000L;
            while (!bucket.isEmpty() && bucket.peekFirst() < windowStart) {
                bucket.pollFirst();
            }
            if (bucket.size() >= Math.max(1, rateLimitPerMinute)) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"data\":null,\"error\":{\"code\":\"请求过快\",\"message\":\"请求频率过高，请稍后再试\"}}");
                return false;
            }
            bucket.addLast(now);
        }

        return true;
    }
}
