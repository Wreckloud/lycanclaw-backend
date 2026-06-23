package com.lycanclaw.backend.stats.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 文章指标同步线程池配置。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
@Configuration
@EnableConfigurationProperties(ArticleMetricProperties.class)
public class ArticleMetricModuleConfig {

    @Bean
    @Qualifier("articleMetricExecutor")
    public ThreadPoolTaskExecutor articleMetricExecutor(ArticleMetricProperties properties) {
        int parallelism = Math.max(1, properties.getFetchParallelism());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 额外线程用于执行外层协调任务，避免它等待同一线程池的抓取任务时耗尽线程。
        executor.setCorePoolSize(parallelism + 1);
        executor.setMaxPoolSize(parallelism + 1);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("article-metric-");
        executor.initialize();
        return executor;
    }
}
