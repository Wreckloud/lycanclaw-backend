package com.lycanclaw.backend.content.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ContentProperties.class)
public class ContentModuleConfig {
}
