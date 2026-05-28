package com.lycanclaw.backend.tag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 标签模块装配配置
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(TagProperties.class)
public class TagModuleConfig {
}
