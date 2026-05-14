package com.lycanclaw.backend.music.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MusicUpstreamProperties.class)
public class MusicModuleConfig {
}
