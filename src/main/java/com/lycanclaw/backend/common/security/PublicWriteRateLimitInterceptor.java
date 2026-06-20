package com.lycanclaw.backend.common.security;

import com.lycanclaw.backend.common.api.ErrorCode;
import io.micrometer.common.lang.NonNull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 匿名写接口限流拦截器。
 *
 * @author Wreckloud
 * @since 2026-06-20
 */
@Component
public class PublicWriteRateLimitInterceptor implements HandlerInterceptor {

    private final ClientIpResolver clientIpResolver;
    private final InMemorySlidingWindowRateLimiter rateLimiter;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Value("${lycan.security.public-write-rate-limit-per-minute:60}")
    private int rateLimitPerMinute;

    public PublicWriteRateLimitInterceptor(
            ClientIpResolver clientIpResolver,
            InMemorySlidingWindowRateLimiter rateLimiter,
            ApiErrorResponseWriter apiErrorResponseWriter
    ) {
        this.clientIpResolver = clientIpResolver;
        this.rateLimiter = rateLimiter;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String clientIp = clientIpResolver.resolve(request);
        String key = "public-write:" + clientIp + ":" + request.getRequestURI();
        if (rateLimiter.allow(key, rateLimitPerMinute)) {
            return true;
        }

        apiErrorResponseWriter.write(response, 429, ErrorCode.PUBLIC_WRITE_RATE_LIMITED);
        return false;
    }
}
