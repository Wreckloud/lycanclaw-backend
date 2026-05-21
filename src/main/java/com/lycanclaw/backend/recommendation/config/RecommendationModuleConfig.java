package com.lycanclaw.backend.recommendation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @Description 推荐模块装配配置
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationModuleConfig {
}
