package com.lycanclaw.backend.music.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @Description 音乐模块装配配置
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Configuration
@EnableConfigurationProperties(MusicUpstreamProperties.class)
public class MusicModuleConfig {
}
