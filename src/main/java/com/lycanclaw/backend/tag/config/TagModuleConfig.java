package com.lycanclaw.backend.tag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 标签模块配置类。
 * 用于注册标签模块相关 Bean 与运行配置。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(TagProperties.class)
public class TagModuleConfig {
}
