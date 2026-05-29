package com.lycanclaw.backend.stats.service;

import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.springframework.stereotype.Service;

/**
 * 提供Pageview相关业务能力。
 *
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
     * 获取pageview。
     */

    public int getPageview(String path) {
        return walineGatewayClient.fetchPageview(normalizePath(path));
    }
    /**
     * 执行update pageview操作。
     */

    public int updatePageview(String path) {
        return walineGatewayClient.increasePageview(normalizePath(path));
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 参数不能为空");
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
