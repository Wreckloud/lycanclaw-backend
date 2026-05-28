package com.lycanclaw.backend.common.config;

import com.lycanclaw.backend.common.security.AdminAuthInterceptor;
import com.lycanclaw.backend.common.security.PublicAccessLogInterceptor;
import com.lycanclaw.backend.common.security.PublicMusicRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 安全拦截器注册配置
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final PublicMusicRateLimitInterceptor publicMusicRateLimitInterceptor;
    private final PublicAccessLogInterceptor publicAccessLogInterceptor;

    public WebSecurityConfig(
            AdminAuthInterceptor adminAuthInterceptor,
            PublicMusicRateLimitInterceptor publicMusicRateLimitInterceptor,
            PublicAccessLogInterceptor publicAccessLogInterceptor
    ) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.publicMusicRateLimitInterceptor = publicMusicRateLimitInterceptor;
        this.publicAccessLogInterceptor = publicAccessLogInterceptor;
    }

    /**
     * 注册拦截器：
     * - 管理员 API 需要管理员令牌；
     * - 公开音乐接口应用限流规则。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns(
                        "/api/music/auth/**",               // 音乐认证接口
                        "/api/recommendations/admin/**",    // 推荐管理接口
                        "/api/admin/auth/**",               // 管理员会话接口（exchange 放行）
                        "/api/admin/ops/**",                // 运维检查接口
                        "/api/admin/dashboard/**",          // 仪表盘接口
                        "/api/admin/governance/**"          // 治理接口
                )
                .excludePathPatterns("/api/admin/auth/waline/exchange");

        registry.addInterceptor(publicMusicRateLimitInterceptor)
                .addPathPatterns(
                        "/api/music/ranking/**",  // 排行榜接口
                        "/api/music/track/**",    // 歌曲信息接口
                        "/api/music/queue/**"     // 播放队列接口
                );

        registry.addInterceptor(publicAccessLogInterceptor)
                .addPathPatterns(
                        "/api/comments/**",
                        "/api/stats/**",
                        "/api/recommendations/**",
                        "/api/tags/**",
                        "/api/contributions/**",
                        "/api/music/**"
                )
                .excludePathPatterns(
                        "/api/music/auth/**",
                        "/api/recommendations/admin/**",
                        "/api/admin/**"
                );
    }
}
