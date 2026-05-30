package com.lycanclaw.backend.common.security;

import com.lycanclaw.backend.common.api.ErrorCode;
import io.micrometer.common.lang.NonNull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 音乐队列写入鉴权拦截器。
 * 用于保护队列写操作接口，避免被匿名请求改写播放状态。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class MusicQueueWriteAuthInterceptor implements HandlerInterceptor {

    private final AdminTokenAuthService adminTokenAuthService;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    @Value("${lycan.security.music-queue-write-require-admin:true}")
    private boolean requireAdminForQueueWrite;

    public MusicQueueWriteAuthInterceptor(
            AdminTokenAuthService adminTokenAuthService,
            ApiErrorResponseWriter apiErrorResponseWriter
    ) {
        this.adminTokenAuthService = adminTokenAuthService;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    /**
     * 可配置地保护队列写操作：
     * - false：保持公开（兼容现有前端）；
     * - true：要求管理凭证。
     */
    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) throws Exception {
        if (!requireAdminForQueueWrite) {
            return true;
        }
        String token = request.getHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER);
        if (adminTokenAuthService.authenticate(token).isEmpty()) {
            apiErrorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.ADMIN_TOKEN_INVALID);
            return false;
        }
        return true;
    }
}
