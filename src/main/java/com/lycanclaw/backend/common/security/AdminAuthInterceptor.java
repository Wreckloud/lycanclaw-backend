package com.lycanclaw.backend.common.security;

import com.lycanclaw.backend.common.api.ErrorCode;
import io.micrometer.common.lang.NonNull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理接口鉴权拦截器。
 * 用于管理端请求的鉴权、限流与访问日志记录。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);

    private final ClientIpResolver clientIpResolver;
    private final AdminTokenAuthService adminTokenAuthService;
    private final InMemorySlidingWindowRateLimiter rateLimiter;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Value("${lycan.security.auth-rate-limit-per-minute}")
    private int rateLimitPerMinute;

    @Value("${lycan.security.admin-auth-log-enabled:true}")
    private boolean adminAuthLogEnabled;

    public AdminAuthInterceptor(
            ClientIpResolver clientIpResolver,
            AdminTokenAuthService adminTokenAuthService,
            InMemorySlidingWindowRateLimiter rateLimiter,
            ApiErrorResponseWriter apiErrorResponseWriter
    ) {
        this.clientIpResolver = clientIpResolver;
        this.adminTokenAuthService = adminTokenAuthService;
        this.rateLimiter = rateLimiter;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    /**
     * 访问顺序：
     * 1) 先按来源 IP + URI 限流，减少暴力探测成本；
     * 2) 再校验管理员凭证（静态 token 或 Waline 会话 token）。
     */
    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {
        String clientIp = clientIpResolver.resolve(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (!allowByRateLimit(clientIp, uri)) {
            logDenied(clientIp, method, uri, "rate_limited");
            apiErrorResponseWriter.write(response, 429, ErrorCode.ADMIN_RATE_LIMITED);
            return false;
        }

        String token = request.getHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER);
        var principal = adminTokenAuthService.authenticate(token);
        if (principal.isEmpty()) {
            logDenied(clientIp, method, uri, "invalid_token");
            apiErrorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.ADMIN_TOKEN_INVALID);
            return false;
        }

        request.setAttribute(AdminAuthConstants.ADMIN_PRINCIPAL_ATTR, principal.get());
        logAllowed(clientIp, method, uri);
        return true;
    }

    /**
     * 管理接口限流：按 clientIp + URI 的分钟窗口限流。
     */
    private boolean allowByRateLimit(String clientIp, String uri) {
        return rateLimiter.allow("admin:" + clientIp + ":" + uri, rateLimitPerMinute);
    }

    private void logDenied(String clientIp, String method, String uri, String reason) {
        if (!adminAuthLogEnabled) {
            return;
        }
        log.warn("admin_access_denied ip={} method={} uri={} reason={}", clientIp, method, uri, reason);
    }

    private void logAllowed(String clientIp, String method, String uri) {
        if (!adminAuthLogEnabled) {
            return;
        }
        log.info("admin_access_allowed ip={} method={} uri={}", clientIp, method, uri);
    }
}
