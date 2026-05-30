package com.lycanclaw.backend.waline.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Waline模块配置类。
 * 用于注册Waline模块相关 Bean 与运行配置。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(WalineProperties.class)
public class WalineModuleConfig {
}
