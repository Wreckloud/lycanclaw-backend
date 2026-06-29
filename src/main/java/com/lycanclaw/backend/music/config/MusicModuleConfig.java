package com.lycanclaw.backend.music.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 音乐模块配置入口。
 * 注册音乐业务和上游服务的类型化配置。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@EnableConfigurationProperties({MusicProperties.class, MusicUpstreamProperties.class})
public class MusicModuleConfig {
}
