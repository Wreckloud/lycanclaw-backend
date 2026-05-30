package com.lycanclaw.backend.recommendation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 推荐模块配置类。
 * 用于注册推荐模块相关 Bean 与运行配置。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationModuleConfig {
}
