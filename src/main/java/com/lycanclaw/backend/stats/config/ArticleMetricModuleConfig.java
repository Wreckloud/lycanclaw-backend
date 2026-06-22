package com.lycanclaw.backend.stats.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(ArticleMetricProperties.class)
public class ArticleMetricModuleConfig {

    @Bean
    @Qualifier("articleMetricExecutor")
    public ThreadPoolTaskExecutor articleMetricExecutor(ArticleMetricProperties properties) {
        int parallelism = Math.max(1, properties.getFetchParallelism());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism + 1);
        executor.setMaxPoolSize(parallelism + 1);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("article-metric-");
        executor.initialize();
        return executor;
    }
}
