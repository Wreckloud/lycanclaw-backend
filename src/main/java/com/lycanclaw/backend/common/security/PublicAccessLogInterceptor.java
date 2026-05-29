package com.lycanclaw.backend.common.security;

import io.micrometer.common.lang.NonNull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 安全组件。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class PublicAccessLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PublicAccessLogInterceptor.class);

    private final ClientIpResolver clientIpResolver;

    @Value("${lycan.security.public-access-log-enabled:true}")
    private boolean publicAccessLogEnabled;

    public PublicAccessLogInterceptor(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * 记录公共 API 的访问来源，便于后续做访客来源与风控分析。
     */
    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        if (!publicAccessLogEnabled) {
            return true;
        }
        String clientIp = clientIpResolver.resolve(request);
        String userAgent = request.getHeader("User-Agent");
        log.info(
                "public_api_access ip={} method={} uri={} ua={}",
                clientIp,
                request.getMethod(),
                request.getRequestURI(),
                userAgent == null ? "" : userAgent
        );
        return true;
    }
}
