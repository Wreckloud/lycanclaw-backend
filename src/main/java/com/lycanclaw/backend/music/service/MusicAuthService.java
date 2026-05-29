package com.lycanclaw.backend.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.common.json.JsonNodeExtractors;
import com.lycanclaw.backend.music.dto.MusicLoginStatusDto;
import com.lycanclaw.backend.music.model.MusicQrLoginStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MusicAuthService：
 * 处理扫码登录、状态查询和刷新登录。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class MusicAuthService {

    private final NcmUpstreamClient upstreamClient;
    private final MusicAuthSessionService sessionService;
    private final JsonNodeExtractors jsonNodeExtractors;

    public MusicAuthService(
            NcmUpstreamClient upstreamClient,
            MusicAuthSessionService sessionService,
            JsonNodeExtractors jsonNodeExtractors
    ) {
        this.upstreamClient = upstreamClient;
        this.sessionService = sessionService;
        this.jsonNodeExtractors = jsonNodeExtractors;
    }

    // 第一步：向上游申请二维码 key（unikey）。
    public Map<String, Object> createQrKey() {
        JsonNode node = upstreamClient.get("/login/qr/key", Map.of("timestamp", String.valueOf(System.currentTimeMillis())));
        String key = jsonNodeExtractors.findText(node, "unikey")
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
        data.put("qrurl", jsonNodeExtractors.findText(node, "qrurl").orElse(""));
        data.put("qrimg", jsonNodeExtractors.findText(node, "qrimg").orElse(""));
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

        int code = jsonNodeExtractors.findInt(node, "code").orElse(-1);
        MusicQrLoginStatus status = MusicQrLoginStatus.fromCode(code);

        if (status.loggedIn()) {
            String cookie = jsonNodeExtractors.findText(node, "cookie")
                    .orElseThrow(() -> new IllegalStateException("登录成功但未获取到 Cookie"));
            sessionService.saveCookie(cookie);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code);
        data.put("status", status.value());
        data.put("message", status.message());
        data.put("loggedIn", status.loggedIn());
        return data;
    }

    // 使用已保存 cookie 查询当前登录账号信息。
    public MusicLoginStatusDto loginStatus() {
        if (!sessionService.hasCookie()) {
            return new MusicLoginStatusDto(false, "当前未登录", "", "", "");
        }

        JsonNode node = upstreamClient.get("/login/status", Map.of(
                "cookie", sessionService.getCookie(),
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));

        boolean loggedIn = jsonNodeExtractors.findInt(node, "code").orElse(-1) == 200
                && jsonNodeExtractors.findText(node, "nickname").isPresent();

        return new MusicLoginStatusDto(
                loggedIn,
                loggedIn ? "已登录" : "登录态无效",
                jsonNodeExtractors.findText(node, "nickname").orElse(""),
                jsonNodeExtractors.findText(node, "userId").orElse(""),
                jsonNodeExtractors.findText(node, "avatarUrl").orElse("")
        );
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

        int code = jsonNodeExtractors.findInt(node, "code").orElse(-1);
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

}
