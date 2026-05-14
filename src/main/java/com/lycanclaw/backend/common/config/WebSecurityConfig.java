package com.lycanclaw.backend.common.config;

import com.lycanclaw.backend.common.security.AdminAuthInterceptor;
import com.lycanclaw.backend.common.security.PublicMusicRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final PublicMusicRateLimitInterceptor publicMusicRateLimitInterceptor;

    public WebSecurityConfig(
            AdminAuthInterceptor adminAuthInterceptor,
            PublicMusicRateLimitInterceptor publicMusicRateLimitInterceptor
    ) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.publicMusicRateLimitInterceptor = publicMusicRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/music/auth/**");

        registry.addInterceptor(publicMusicRateLimitInterceptor)
                .addPathPatterns("/api/music/ranking/**", "/api/music/track/**", "/api/music/queue/**");
    }
}
