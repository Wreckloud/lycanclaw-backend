package com.lycanclaw.backend.common.security;

import com.lycanclaw.backend.admin.service.AdminRiskControlService;
import com.lycanclaw.backend.common.api.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description 管理员接口鉴权与限流拦截器
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String TOKEN_HEADER = "X-Lycan-Admin-Token";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final AdminRiskControlService adminRiskControlService;

    @Value("${lycan.security.admin-token}")
    private String adminToken;

    @Value("${lycan.security.auth-rate-limit-per-minute}")
    private int rateLimitPerMinute;

    @Value("${lycan.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    private final Map<String, ArrayDeque<Long>> rateBuckets = new ConcurrentHashMap<>();

    public AdminAuthInterceptor(AdminRiskControlService adminRiskControlService) {
        this.adminRiskControlService = adminRiskControlService;
    }

    /**
     * 访问顺序：
     * 1) 先按来源 IP + URI 限流，减少暴力探测成本；
     * 2) 再校验来源 IP 是否在白名单；
     * 3) 最后校验管理员令牌。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = extractClientIp(request);

        if (!allowByRateLimit(clientIp, request.getRequestURI())) {
            reject(response, 429, ErrorCodes.ADMIN_RATE_LIMITED, "请求频率过高，请稍后再试");
            return false;
        }

        if (!adminRiskControlService.isIpAllowed(clientIp)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, ErrorCodes.ADMIN_IP_FORBIDDEN, "当前 IP 不在管理端白名单中");
            return false;
        }

        String token = request.getHeader(TOKEN_HEADER);
        if (!constantTimeEquals(adminToken, token)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCodes.ADMIN_TOKEN_INVALID, "管理员令牌无效");
            return false;
        }

        return true;
    }

    /**
     * 若部署在反向代理后，优先读取 X-Forwarded-For 的首个地址。
     */
    private String extractClientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String first = forwardedFor.split(",")[0].trim();
                if (!first.isBlank()) {
                    return adminRiskControlService.normalizeIp(first);
                }
            }
        }
        return adminRiskControlService.normalizeIp(request.getRemoteAddr());
    }

    private boolean allowByRateLimit(String clientIp, String uri) {
        String key = clientIp + ":" + uri;
        long now = Instant.now().toEpochMilli();
        ArrayDeque<Long> bucket = rateBuckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            long windowStart = now - 60_000L;
            while (!bucket.isEmpty() && bucket.peekFirst() < windowStart) {
                bucket.pollFirst();
            }
            if (bucket.size() >= Math.max(1, rateLimitPerMinute)) {
                return false;
            }
            bucket.addLast(now);
            return true;
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8);
        byte[] right = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"data\":null,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}"
        );
    }
}
