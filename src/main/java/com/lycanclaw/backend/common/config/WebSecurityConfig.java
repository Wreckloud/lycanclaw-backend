package com.lycanclaw.backend.common.config;

import com.lycanclaw.backend.common.security.AdminAuthInterceptor;
import com.lycanclaw.backend.common.security.PublicAccessLogInterceptor;
import com.lycanclaw.backend.common.security.PublicMusicRateLimitInterceptor;
import com.lycanclaw.backend.common.security.PublicWriteRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 安全拦截配置。
 * 用于注册管理鉴权、访问限流与公共日志拦截器。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final PublicMusicRateLimitInterceptor publicMusicRateLimitInterceptor;
    private final PublicWriteRateLimitInterceptor publicWriteRateLimitInterceptor;
    private final PublicAccessLogInterceptor publicAccessLogInterceptor;

    public WebSecurityConfig(
            AdminAuthInterceptor adminAuthInterceptor,
            PublicMusicRateLimitInterceptor publicMusicRateLimitInterceptor,
            PublicWriteRateLimitInterceptor publicWriteRateLimitInterceptor,
            PublicAccessLogInterceptor publicAccessLogInterceptor
    ) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.publicMusicRateLimitInterceptor = publicMusicRateLimitInterceptor;
        this.publicWriteRateLimitInterceptor = publicWriteRateLimitInterceptor;
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
                        "/api/admin/**"                     // 管理端统一接口（exchange 放行）
                )
                .excludePathPatterns("/api/admin/auth/waline/exchange");

        registry.addInterceptor(publicMusicRateLimitInterceptor)
                .addPathPatterns(
                        "/api/music/ranking/**",  // 排行榜接口
                        "/api/music/track/**",    // 歌曲信息接口
                        "/api/music/queue/**",    // 播放队列接口
                        "/api/music/flow/**"      // 播放流接口
                );

        registry.addInterceptor(publicWriteRateLimitInterceptor)
                .addPathPatterns(
                        "/api/stats/pageview",
                        "/api/article-metrics/batch",
                        "/api/analytics/visit/**",
                        "/api/analytics/identity/**",
                        "/api/encouragement/**",
                        "/api/music/analytics/**"
                );

        registry.addInterceptor(publicAccessLogInterceptor)
                .addPathPatterns(
                        "/api/comments/**",
                        "/api/article-metrics/**",
                        "/api/stats/**",
                        "/api/recommendations/**",
                        "/api/analytics/**",
                        "/api/encouragement/**",
                        "/api/music/**"
                )
                .excludePathPatterns(
                        "/api/music/auth/**",
                        "/api/recommendations/admin/**",
                        "/api/admin/**"
                );
    }
}
