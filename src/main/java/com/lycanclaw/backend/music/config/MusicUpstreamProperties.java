package com.lycanclaw.backend.music.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 音乐上游配置模型
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@ConfigurationProperties(prefix = "lycan.music.upstream")
public class MusicUpstreamProperties {

    /**
     * api-enhanced 服务地址，例如：http://127.0.0.1:3000
     */
    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
