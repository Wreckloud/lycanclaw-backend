package com.lycanclaw.backend.music.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class MusicAuthSessionService {

    private final AtomicReference<String> musicCookie = new AtomicReference<>();

    public void saveCookie(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("登录 Cookie 为空，无法保存");
        }
        musicCookie.set(cookie.trim());
    }

    public String getCookie() {
        return musicCookie.get();
    }

    public boolean hasCookie() {
        String value = musicCookie.get();
        return value != null && !value.isBlank();
    }

    public void clear() {
        musicCookie.set(null);
    }
}
