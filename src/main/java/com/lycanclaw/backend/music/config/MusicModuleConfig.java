package com.lycanclaw.backend.music.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MusicModuleConfig：
 * 定义MusicModule相关配置。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(MusicUpstreamProperties.class)
public class MusicModuleConfig {
}
