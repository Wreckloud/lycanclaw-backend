package com.lycanclaw.backend.tag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @Description 标签模块装配配置
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(TagProperties.class)
public class TagModuleConfig {
}
