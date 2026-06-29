package com.lycanclaw.backend.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.common.json.JsonNodeExtractors;
import com.lycanclaw.backend.music.dto.MusicLoginStatusDto;
import com.lycanclaw.backend.music.model.MusicQrLoginStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 音乐登录服务。
 * 处理扫码登录、状态查询和刷新登录。
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

    /**
     * 创建二维码登录 key（unikey）。
     */
    public Map<String, Object> createQrKey() {
        JsonNode node = upstreamClient.get("/login/qr/key", Map.of("timestamp", String.valueOf(System.currentTimeMillis())));
        String key = jsonNodeExtractors.findText(node, "unikey")
                .orElseThrow(() -> new IllegalStateException("上游未返回二维码 key"));

        return Map.of("key", key);
    }

    /**
     * 根据 key 生成二维码图片与登录链接。
     */
    public Map<String, Object> createQrImage(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key 参数不能为空");
        }
        JsonNode node = upstreamClient.get("/login/qr/create", Map.of(
                "key", key,
                "qrimg", "true",
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));

        String qrImage = jsonNodeExtractors.findText(node, "qrimg")
                .orElseThrow(() -> new IllegalStateException("上游未返回二维码图片"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("qrurl", jsonNodeExtractors.findText(node, "qrurl").orElse(""));
        data.put("qrimg", qrImage);
        return data;
    }

    /**
     * 轮询二维码登录状态；登录成功后写入本地会话。
     */
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

    /**
     * 查询当前音乐账号登录状态。
     */
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
        if (!loggedIn) {
            sessionService.clear();
        }

        return new MusicLoginStatusDto(
                loggedIn,
                loggedIn ? "已登录" : "登录态无效",
                jsonNodeExtractors.findText(node, "nickname").orElse(""),
                jsonNodeExtractors.findText(node, "userId").orElse(""),
                jsonNodeExtractors.findText(node, "avatarUrl").orElse("")
        );
    }

    /**
     * 退出登录并清除本地会话。
     */
    public Map<String, Object> logout() {
        sessionService.clear();
        return Map.of("message", "已退出并清除本地登录态");
    }

}
