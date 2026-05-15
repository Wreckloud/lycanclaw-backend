package com.lycanclaw.backend.music.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @Description 音乐登录会话存储服务
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Service
public class MusicAuthSessionService {

    private final AtomicReference<String> musicCookie = new AtomicReference<>();

    /**
     * 保存最新可用的登录 cookie。
     */
    public void saveCookie(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("登录 Cookie 为空，无法保存");
        }
        musicCookie.set(cookie.trim());
    }

    /**
     * 获取当前登录 cookie 原文。
     */
    public String getCookie() {
        return musicCookie.get();
    }

    /**
     * 判断内存中是否已有有效登录态。
     */
    public boolean hasCookie() {
        String value = musicCookie.get();
        return value != null && !value.isBlank();
    }

    /**
     * 清理当前登录态。
     */
    public void clear() {
        musicCookie.set(null);
    }
}
