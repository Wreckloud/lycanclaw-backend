package com.lycanclaw.backend.music.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.music.service.MusicAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 音乐登录管理接口
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/music/auth")
@Tag(name = "音乐登录", description = "管理员二维码登录与登录态管理")
@SecurityRequirement(name = "adminToken")
public class MusicAuthController {

    private final MusicAuthService musicAuthService;

    public MusicAuthController(MusicAuthService musicAuthService) {
        this.musicAuthService = musicAuthService;
    }

    /**
     * 申请二维码 key，作为后续生成二维码和轮询状态的凭据。
     */
    @Operation(summary = "申请二维码 key")
    @GetMapping("/qr/key")
    public ApiResponse<Map<String, Object>> qrKey() {
        return ApiResponse.ok(musicAuthService.createQrKey());
    }

    /**
     * 根据 key 生成二维码图片和扫码链接。
     */
    @Operation(summary = "生成二维码图片")
    @GetMapping("/qr/create")
    public ApiResponse<Map<String, Object>> qrCreate(
            @Parameter(description = "二维码 key", required = true)
            @RequestParam("key") String key
    ) {
        return ApiResponse.ok(musicAuthService.createQrImage(key));
    }

    /**
     * 轮询二维码登录状态，803 表示登录成功。
     */
    @Operation(summary = "轮询扫码状态")
    @GetMapping("/qr/check")
    public ApiResponse<Map<String, Object>> qrCheck(
            @Parameter(description = "二维码 key", required = true)
            @RequestParam("key") String key
    ) {
        return ApiResponse.ok(musicAuthService.checkQrStatus(key));
    }

    /**
     * 查询当前后端保存的网易云登录状态。
     */
    @Operation(summary = "查询登录状态")
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(musicAuthService.loginStatus());
    }

    /**
     * 刷新登录态，延长 cookie 有效期。
     */
    @Operation(summary = "刷新登录态")
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh() {
        return ApiResponse.ok(musicAuthService.refreshLogin());
    }

    /**
     * 清理后端本地缓存的登录态。
     */
    @Operation(summary = "退出并清理登录态")
    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        return ApiResponse.ok(musicAuthService.logout());
    }
}
