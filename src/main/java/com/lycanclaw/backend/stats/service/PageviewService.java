package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.common.path.WebPathNormalizer;
import com.lycanclaw.backend.common.time.AppTimeProvider;
import com.lycanclaw.backend.content.service.ContentCatalogService;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 阅读量服务。
 * 处理页面阅读量写入、累加与查询。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class PageviewService {

    private static final Logger log = LoggerFactory.getLogger(PageviewService.class);
    private static final long DEDUPLICATION_MILLIS = 30 * 60 * 1000L;

    private final WalineGatewayClient walineGatewayClient;
    private final ArticleMetricService articleMetricService;
    private final ContentCatalogService contentCatalogService;
    private final AppTimeProvider appTimeProvider;
    private final Map<String, Long> recentPageviews = new HashMap<>();

    public PageviewService(
            WalineGatewayClient walineGatewayClient,
            ArticleMetricService articleMetricService,
            ContentCatalogService contentCatalogService,
            AppTimeProvider appTimeProvider
    ) {
        this.walineGatewayClient = walineGatewayClient;
        this.articleMetricService = articleMetricService;
        this.contentCatalogService = contentCatalogService;
        this.appTimeProvider = appTimeProvider;
    }

    /**
     * 增加指定文章路径的阅读量。
     */
    public int updatePageview(String path, String clientIp) {
        String normalizedPath = contentCatalogService.requirePublishedArticle(path).path();
        String deduplicationKey = clientIp + "|" + WebPathNormalizer.normalize(normalizedPath);
        long now = appTimeProvider.nowOffsetDateTime().toInstant().toEpochMilli();

        // 先占用去重键，防止同一访客的并发请求重复增加 Waline 计数。
        synchronized (recentPageviews) {
            recentPageviews.entrySet().removeIf(entry -> now - entry.getValue() >= DEDUPLICATION_MILLIS);
            Long previous = recentPageviews.get(deduplicationKey);
            if (previous != null) {
                return articleMetricService.find(normalizedPath).pageviewCount();
            }
            recentPageviews.put(deduplicationKey, now);
        }

        int pageviewCount;
        try {
            pageviewCount = walineGatewayClient.increasePageview(normalizedPath);
        } catch (RuntimeException ex) {
            synchronized (recentPageviews) {
                recentPageviews.remove(deduplicationKey, now);
            }
            throw ex;
        }

        try {
            articleMetricService.updatePageview(normalizedPath, pageviewCount);
        } catch (RuntimeException ex) {
            log.warn("failed to save pageview snapshot, path={}, count={}", normalizedPath, pageviewCount, ex);
        }
        return pageviewCount;
    }
}
