package com.lycanclaw.backend.analytics.service;

import jakarta.annotation.PreDestroy;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.ConfigBuilder;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * IP 地区解析服务。
 * 在外部 XDB 文件可用时解析国家、省市和运营商，缺失时返回空地区信息。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Service
public class IpRegionService {

    private final Ip2Region ip2Region;

    public IpRegionService(
            @Value("${lycan.analytics.ip-region.v4-xdb-path:}") String v4Path,
            @Value("${lycan.analytics.ip-region.v6-xdb-path:}") String v6Path
    ) {
        this.ip2Region = createService(v4Path, v6Path);
    }

    /**
     * 查询并格式化地区文本；未配置数据库或查询失败时返回空字符串。
     */
    public String resolve(String ip) {
        if (ip2Region == null || ip == null || ip.isBlank()) {
            return "";
        }
        try {
            String region = ip2Region.search(ip.trim());
            return format(region);
        } catch (Exception ignored) {
            return "";
        }
    }

    public String format(String region) {
        if (region == null || region.isBlank()) {
            return "";
        }
        String trimmed = region.trim();
        if (!trimmed.contains("|")) {
            return trimmed.replaceAll("\\s+", " ");
        }

        List<String> parts = new ArrayList<>();
        for (String item : trimmed.split("\\|")) {
            String value = item == null ? "" : item.trim();
            if (value.isBlank() || "0".equals(value) || "中国".equals(value)) {
                continue;
            }
            parts.add(value);
        }
        if (parts.isEmpty()) {
            return "";
        }

        int lastIndex = parts.size() - 1;
        parts.set(lastIndex, normalizeIsp(parts.get(lastIndex)));
        return String.join(" ", parts);
    }

    public boolean isAvailable() {
        return ip2Region != null;
    }

    @PreDestroy
    public void close() {
        if (ip2Region != null) {
            try {
                ip2Region.close();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Ip2Region createService(String v4Path, String v6Path) {
        Config v4 = buildConfig(v4Path, true);
        Config v6 = buildConfig(v6Path, false);
        if (v4 == null && v6 == null) {
            return null;
        }
        try {
            return Ip2Region.create(v4, v6);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Config buildConfig(String value, boolean ipv4) {
        if (value == null || value.isBlank() || !Files.isRegularFile(Path.of(value.trim()))) {
            return null;
        }
        ConfigBuilder builder = Config.custom()
                .setCachePolicy(Config.VIndexCache)
                .setSearchers(4)
                .setXdbPath(value.trim());
        try {
            return ipv4 ? builder.asV4() : builder.asV6();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeIsp(String value) {
        if (value.startsWith("中国")) {
            return value;
        }
        if (value.contains("电信") || value.contains("联通") || value.contains("移动")) {
            return "中国" + value;
        }
        return value;
    }
}
