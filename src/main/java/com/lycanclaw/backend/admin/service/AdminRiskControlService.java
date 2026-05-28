package com.lycanclaw.backend.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @Description 管理端基础风控（IP 白名单）
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Service
public class AdminRiskControlService {

    @Value("${lycan.security.admin-ip-whitelist:127.0.0.1,::1}")
    private String adminIpWhitelist;

    /**
     * 支持精确匹配和前缀匹配（末尾 *）。
     */
    public boolean isIpAllowed(String clientIp) {
        List<String> whitelist = parseWhitelist();
        if (whitelist.isEmpty()) {
            return true;
        }

        String normalized = normalizeIp(clientIp);
        if (normalized.isBlank()) {
            return false;
        }

        for (String item : whitelist) {
            if (item.equals(normalized)) {
                return true;
            }
            if (item.endsWith("*")) {
                String prefix = item.substring(0, item.length() - 1);
                if (!prefix.isBlank() && normalized.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String whitelistSummary() {
        List<String> whitelist = parseWhitelist();
        if (whitelist.isEmpty()) {
            return "未配置（默认允许所有来源）";
        }
        return String.join(", ", whitelist);
    }

    private List<String> parseWhitelist() {
        if (adminIpWhitelist == null || adminIpWhitelist.isBlank()) {
            return List.of();
        }
        String[] raw = adminIpWhitelist.split(",");
        List<String> list = new ArrayList<>();
        for (String item : raw) {
            String normalized = normalizeIp(item);
            if (!normalized.isBlank()) {
                list.add(normalized);
            }
        }
        return list;
    }

    /**
     * 兼容 IPv4-mapped IPv6，例如 ::ffff:127.0.0.1。
     */
    public String normalizeIp(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("::ffff:")) {
            trimmed = trimmed.substring("::ffff:".length());
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
