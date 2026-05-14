package com.lycanclaw.backend.music.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.music.service.MusicAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/music/auth")
public class MusicAuthController {

    private final MusicAuthService musicAuthService;

    public MusicAuthController(MusicAuthService musicAuthService) {
        this.musicAuthService = musicAuthService;
    }

    @GetMapping("/qr/key")
    public ApiResponse<Map<String, Object>> qrKey() {
        return ApiResponse.ok(musicAuthService.createQrKey());
    }

    @GetMapping("/qr/create")
    public ApiResponse<Map<String, Object>> qrCreate(@RequestParam("key") String key) {
        return ApiResponse.ok(musicAuthService.createQrImage(key));
    }

    @GetMapping("/qr/check")
    public ApiResponse<Map<String, Object>> qrCheck(@RequestParam("key") String key) {
        return ApiResponse.ok(musicAuthService.checkQrStatus(key));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(musicAuthService.loginStatus());
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh() {
        return ApiResponse.ok(musicAuthService.refreshLogin());
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        return ApiResponse.ok(musicAuthService.logout());
    }
}
