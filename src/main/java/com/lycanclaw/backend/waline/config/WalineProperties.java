package com.lycanclaw.backend.waline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Waline配置属性。
 * 用于承载Waline相关外部配置项。
 * @author Wreckloud
 * @since 2026-05-15
 */
@ConfigurationProperties(prefix = "lycan.waline")
public class WalineProperties {

    /**
     * Waline 服务根地址。
     */
    private String baseUrl = "http://127.0.0.1:8360";

    /**
     * 对 Waline 发起服务端请求时使用的站点来源。
     */
    private String publicUrl = "http://localhost:5173";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }
}
