package com.lycanclaw.backend.music.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 音乐模块装配配置
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(MusicUpstreamProperties.class)
public class MusicModuleConfig {
}
