package com.lycanclaw.backend.common.security;

import com.lycanclaw.backend.admin.service.AdminRiskControlService;
import com.lycanclaw.backend.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 管理员接口鉴权与限流拦截器
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String TOKEN_HEADER = "X-Lycan-Admin-Token";

    private final AdminRiskControlService adminRiskControlService;
    private final ClientIpResolver clientIpResolver;
    private final InMemorySlidingWindowRateLimiter rateLimiter;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Value("${lycan.security.admin-token}")
    private String adminToken;

    @Value("${lycan.security.auth-rate-limit-per-minute}")
    private int rateLimitPerMinute;

    public AdminAuthInterceptor(
            AdminRiskControlService adminRiskControlService,
            ClientIpResolver clientIpResolver,
            InMemorySlidingWindowRateLimiter rateLimiter,
            ApiErrorResponseWriter apiErrorResponseWriter
    ) {
        this.adminRiskControlService = adminRiskControlService;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiter = rateLimiter;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    /**
     * 访问顺序：
     * 1) 先按来源 IP + URI 限流，减少暴力探测成本；
     * 2) 再校验来源 IP 是否在白名单；
     * 3) 最后校验管理员令牌。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = clientIpResolver.resolve(request);

        if (!allowByRateLimit(clientIp, request.getRequestURI())) {
            apiErrorResponseWriter.write(response, 429, ErrorCode.ADMIN_RATE_LIMITED);
            return false;
        }

        if (!adminRiskControlService.isIpAllowed(clientIp)) {
            apiErrorResponseWriter.write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.ADMIN_IP_FORBIDDEN);
            return false;
        }

        String token = request.getHeader(TOKEN_HEADER);
        if (!constantTimeEquals(adminToken, token)) {
            apiErrorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.ADMIN_TOKEN_INVALID);
            return false;
        }

        return true;
    }

    /**
     * 管理接口限流：按 clientIp + URI 的分钟窗口限流。
     */
    private boolean allowByRateLimit(String clientIp, String uri) {
        return rateLimiter.allow("admin:" + clientIp + ":" + uri, rateLimitPerMinute);
    }

    /**
     * token 比较方法；
     * 做相对更安全的比较，减少时序攻击风险；
     * (虽然不一定真有人用时序攻击来干我)
     */
    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8);
        byte[] right = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
