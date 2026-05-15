package com.lycanclaw.backend.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @Description 网易云扫码登录服务
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Service
public class MusicAuthService {

    private final NcmUpstreamClient upstreamClient;
    private final MusicAuthSessionService sessionService;

    public MusicAuthService(NcmUpstreamClient upstreamClient, MusicAuthSessionService sessionService) {
        this.upstreamClient = upstreamClient;
        this.sessionService = sessionService;
    }

    // 第一步：向上游申请二维码 key（unikey）。
    public Map<String, Object> createQrKey() {
        JsonNode node = upstreamClient.get("/login/qr/key", Map.of("timestamp", String.valueOf(System.currentTimeMillis())));
        String key = findText(node, "unikey")
                .orElseThrow(() -> new IllegalStateException("上游未返回二维码 key"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("timestamp", Instant.now().toString());
        return data;
    }

    // 第二步：根据 key 生成二维码图片/链接，前端直接展示 qrimg。
    public Map<String, Object> createQrImage(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key 参数不能为空");
        }
        JsonNode node = upstreamClient.get("/login/qr/create", Map.of(
                "key", key,
                "qrimg", "true",
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("qrurl", findText(node, "qrurl").orElse(""));
        data.put("qrimg", findText(node, "qrimg").orElse(""));
        return data;
    }

    // 第三步：轮询扫码状态；803 时写入本地 cookie 会话。
    public Map<String, Object> checkQrStatus(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key 参数不能为空");
        }
        JsonNode node = upstreamClient.get("/login/qr/check", Map.of(
                "key", key,
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));

        int code = findInt(node, "code").orElse(-1);
        String message = statusMessage(code);

        if (code == 803) {
            String cookie = findText(node, "cookie")
                    .orElseThrow(() -> new IllegalStateException("登录成功但未获取到 Cookie"));
            sessionService.saveCookie(cookie);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code);
        data.put("message", message);
        data.put("已登录", code == 803);
        return data;
    }

    // 使用已保存 cookie 查询当前登录账号信息。
    public Map<String, Object> loginStatus() {
        if (!sessionService.hasCookie()) {
            return Map.of(
                    "已登录", false,
                    "message", "当前未登录"
            );
        }

        JsonNode node = upstreamClient.get("/login/status", Map.of(
                "cookie", sessionService.getCookie(),
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));

        boolean loggedIn = findInt(node, "code").orElse(-1) == 200
                && findText(node, "nickname").isPresent();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("已登录", loggedIn);
        data.put("nickname", findText(node, "nickname").orElse(""));
        data.put("userId", findText(node, "userId").orElse(""));
        data.put("avatarUrl", findText(node, "avatarUrl").orElse(""));
        return data;
    }

    // 保活登录态，避免 cookie 过快失效。
    public Map<String, Object> refreshLogin() {
        if (!sessionService.hasCookie()) {
            throw new IllegalStateException("当前未登录，无法刷新登录状态");
        }
        JsonNode node = upstreamClient.get("/login/refresh", Map.of(
                "cookie", sessionService.getCookie(),
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));

        int code = findInt(node, "code").orElse(-1);
        return Map.of(
                "code", code,
                "message", code == 200 ? "刷新成功" : "刷新失败"
        );
    }

    // 仅清理本地会话，不主动调用上游退出接口。
    public Map<String, Object> logout() {
        sessionService.clear();
        return Map.of("message", "已退出并清除本地登录态");
    }

    private String statusMessage(int code) {
        return switch (code) {
            case 800 -> "二维码已过期";
            case 801 -> "等待扫码";
            case 802 -> "已扫码，等待确认";
            case 803 -> "登录成功";
            default -> "未知状态";
        };
    }

    private Optional<String> findText(JsonNode node, String key) {
        if (node == null || node.isNull()) return Optional.empty();
        if (node.has(key) && !node.get(key).isNull()) {
            return Optional.of(String.valueOf(node.get(key).asText("")));
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                Optional<String> child = findText(entry.getValue(), key);
                if (child.isPresent() && !child.get().isBlank()) {
                    return child;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode childNode : node) {
                Optional<String> child = findText(childNode, key);
                if (child.isPresent() && !child.get().isBlank()) {
                    return child;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> findInt(JsonNode node, String key) {
        Optional<String> text = findText(node, key);
        if (text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(text.get()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
