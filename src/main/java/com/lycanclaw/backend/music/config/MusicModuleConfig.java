package com.lycanclaw.backend.music.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 音乐模块配置类。
 * 用于注册音乐模块相关 Bean 与运行配置。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@EnableConfigurationProperties({MusicProperties.class, MusicUpstreamProperties.class})
public class MusicModuleConfig {
}
