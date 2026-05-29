package com.lycanclaw.backend.waline.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * WalineModuleConfig：
 * 定义WalineModule相关配置。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(WalineProperties.class)
public class WalineModuleConfig {
}
