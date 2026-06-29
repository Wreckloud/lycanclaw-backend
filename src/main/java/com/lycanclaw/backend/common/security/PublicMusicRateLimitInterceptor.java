package com.lycanclaw.backend.common.security;

import com.lycanclaw.backend.common.api.ErrorCode;
import io.micrometer.common.lang.NonNull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 公开音乐接口限流拦截器。
 * 用于限制公开音乐接口的访问频率。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class PublicMusicRateLimitInterceptor implements HandlerInterceptor {

    @Value("${lycan.security.music-rate-limit-per-minute}")
    private int rateLimitPerMinute;

    private final ClientIpResolver clientIpResolver;
    private final InMemorySlidingWindowRateLimiter rateLimiter;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public PublicMusicRateLimitInterceptor(
            ClientIpResolver clientIpResolver,
            InMemorySlidingWindowRateLimiter rateLimiter,
            ApiErrorResponseWriter apiErrorResponseWriter
    ) {
        this.clientIpResolver = clientIpResolver;
        this.rateLimiter = rateLimiter;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    /**
     * 同一 IP 的全部公开音乐接口共享分钟级额度，避免通过切换路径绕过。
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String clientIp = clientIpResolver.resolve(request);
        boolean allow = rateLimiter.allow("music:" + clientIp, rateLimitPerMinute);
        if (!allow) {
            apiErrorResponseWriter.write(response, 429, ErrorCode.MUSIC_RATE_LIMITED);
            return false;
        }
        return true;
    }
}
