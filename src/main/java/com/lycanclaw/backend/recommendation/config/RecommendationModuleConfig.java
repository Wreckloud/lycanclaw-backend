package com.lycanclaw.backend.recommendation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 推荐模块装配配置
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationModuleConfig {
}
