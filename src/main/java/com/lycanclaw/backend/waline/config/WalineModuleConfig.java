package com.lycanclaw.backend.waline.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @Description Waline 模块装配配置
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(WalineProperties.class)
public class WalineModuleConfig {
}
