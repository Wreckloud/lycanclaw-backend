package com.lycanclaw.backend.analytics.controller;

import com.lycanclaw.backend.analytics.dto.MusicListenSettleRequest;
import com.lycanclaw.backend.analytics.dto.MusicListenSettleResponse;
import com.lycanclaw.backend.analytics.service.MusicListenAnalyticsService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台音乐收听统计接口。
 * 接收播放器按会话上报的累计收听数据。
 * @author Wreckloud
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/api/music/analytics")
@Tag(name = "音乐收听统计", description = "前台音乐播放会话结算")
public class MusicAnalyticsController {

    private final MusicListenAnalyticsService musicListenAnalyticsService;

    public MusicAnalyticsController(MusicListenAnalyticsService musicListenAnalyticsService) {
        this.musicListenAnalyticsService = musicListenAnalyticsService;
    }

    @Operation(summary = "结算音乐收听会话")
    @PostMapping("/settle")
    public ApiResponse<MusicListenSettleResponse> settle(
            @RequestBody MusicListenSettleRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(musicListenAnalyticsService.settle(request, servletRequest));
    }
}
