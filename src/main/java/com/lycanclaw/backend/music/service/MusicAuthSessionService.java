package com.lycanclaw.backend.music.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 音乐登录会话服务。
 * 保存网易云登录 Cookie。
 * @author Wreckloud
 * @since 2026-05-15
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
     * 获取必需登录态；当未登录时直接抛出异常，避免调用方重复判空。
     */
    public String getRequiredCookie() {
        String value = musicCookie.get();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("当前未登录，无法获取登录 Cookie");
        }
        return value;
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
