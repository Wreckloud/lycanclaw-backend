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

    public PageviewService(WalineGatewayClient walineGatewayClient) {
        this.walineGatewayClient = walineGatewayClient;
    }

    /**
     * 查询指定文章路径的阅读量。
     */
    public int getPageview(String path) {
        return walineGatewayClient.fetchPageview(path);
    }

    /**
     * 增加指定文章路径的阅读量。
     */
    public int updatePageview(String path) {
        return walineGatewayClient.increasePageview(path);
    }
}
