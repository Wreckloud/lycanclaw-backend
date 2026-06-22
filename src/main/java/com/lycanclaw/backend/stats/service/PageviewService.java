package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.springframework.stereotype.Service;

/**
 * 阅读量服务。
 * 处理页面阅读量写入、累加与查询。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class PageviewService {

    private final WalineGatewayClient walineGatewayClient;
    private final ArticleMetricService articleMetricService;

    public PageviewService(
            WalineGatewayClient walineGatewayClient,
            ArticleMetricService articleMetricService
    ) {
        this.walineGatewayClient = walineGatewayClient;
        this.articleMetricService = articleMetricService;
    }

    /**
     * 增加指定文章路径的阅读量。
     */
    public int updatePageview(String path) {
        int pageviewCount = walineGatewayClient.increasePageview(path);
        articleMetricService.updatePageview(path, pageviewCount);
        return pageviewCount;
    }
}
