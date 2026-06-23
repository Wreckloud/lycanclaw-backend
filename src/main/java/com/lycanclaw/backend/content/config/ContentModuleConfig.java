package com.lycanclaw.backend.content.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 内容目录模块配置。
 * 启用内容索引配置绑定。
 * @author Wreckloud
 * @since 2026-06-23
 */
@Configuration
@EnableConfigurationProperties(ContentProperties.class)
public class ContentModuleConfig {
}
