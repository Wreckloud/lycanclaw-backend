package com.lycanclaw.backend.waline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Waline 网关配置项
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@ConfigurationProperties(prefix = "lycan.waline")
public class WalineProperties {

    /**
     * Waline 服务根地址。
     */
    private String baseUrl = "http://127.0.0.1:8360";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
