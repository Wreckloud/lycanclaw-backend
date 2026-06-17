package com.lycanclaw.backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 跨域访问配置。
 * 用于统一配置前端可访问的来源域名与请求方法。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${lycan.cors.allowed-origins}")
    private String allowedOriginsRaw;

    /**
     * 开放前端会直接请求的接口路径。
     * /waline/** 是评论区的同源代理入口，本地前端与后端端口不同时也需要跨域。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(parseAllowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);

        registry.addMapping("/waline/**")
                .allowedOrigins(parseAllowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    /**
     * 将配置字符串转为 origin 数组，并过滤空值。
     */
    private String[] parseAllowedOrigins() {
        if (allowedOriginsRaw == null || allowedOriginsRaw.isBlank()) return new String[0];
        return Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }
}
