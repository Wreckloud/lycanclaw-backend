package com.lycanclaw.backend.music.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.music.dto.AboutSequenceStartRequest;
import com.lycanclaw.backend.music.dto.InterruptSingleRequest;
import com.lycanclaw.backend.music.dto.MusicFlowStateDto;
import com.lycanclaw.backend.music.service.MusicFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 音乐流控制器。
 * 提供随机流、关于页顺序流和打断插入的会话级控制接口。
 * @author Wreckloud
 * @since 2026-05-30
 */
@RestController
@RequestMapping("/api/music/flow")
@Tag(name = "音乐播放流", description = "会话级播放模式控制")
public class MusicFlowController {

    private static final String SESSION_HEADER = "X-Lycan-Playback-Session";

    private final MusicFlowService musicFlowService;

    public MusicFlowController(MusicFlowService musicFlowService) {
        this.musicFlowService = musicFlowService;
    }

    /**
     * 启动首页随机流。
     */
    @Operation(summary = "启动首页随机流")
    @PostMapping("/start-random")
    public ApiResponse<MusicFlowStateDto> startRandom(
            @Parameter(hidden = true) @RequestHeader(value = SESSION_HEADER, required = false) String sessionHeader,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(musicFlowService.startRandom(resolveSessionId(sessionHeader, request)));
    }

    /**
     * 启动关于页顺序流。
     */
    @Operation(summary = "启动关于页顺序流")
    @PostMapping("/start-about-sequence")
    public ApiResponse<MusicFlowStateDto> startAboutSequence(
            @Parameter(hidden = true) @RequestHeader(value = SESSION_HEADER, required = false) String sessionHeader,
            @RequestBody AboutSequenceStartRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                musicFlowService.startAboutSequence(
                        resolveSessionId(sessionHeader, request),
                        body == null ? null : body.startSongId()
                )
        );
    }

    /**
     * 打断当前播放并插入单曲。
     */
    @Operation(summary = "打断插入单曲")
    @PostMapping("/interrupt-single")
    public ApiResponse<MusicFlowStateDto> interruptSingle(
            @Parameter(hidden = true) @RequestHeader(value = SESSION_HEADER, required = false) String sessionHeader,
            @RequestBody InterruptSingleRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                musicFlowService.interruptSingle(
                        resolveSessionId(sessionHeader, request),
                        body == null ? null : body.songId(),
                        body == null ? null : body.source()
                )
        );
    }

    /**
     * 推进到下一首。
     */
    @Operation(summary = "推进到下一首")
    @PostMapping("/next")
    public ApiResponse<MusicFlowStateDto> next(
            @Parameter(hidden = true) @RequestHeader(value = SESSION_HEADER, required = false) String sessionHeader,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(musicFlowService.next(resolveSessionId(sessionHeader, request)));
    }

    /**
     * 查询当前播放流状态。
     */
    @Operation(summary = "查询播放流状态")
    @GetMapping("/state")
    public ApiResponse<MusicFlowStateDto> state(
            @Parameter(hidden = true) @RequestHeader(value = SESSION_HEADER, required = false) String sessionHeader,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(musicFlowService.state(resolveSessionId(sessionHeader, request)));
    }

    /**
     * 停止并清空当前播放会话。
     */
    @Operation(summary = "停止当前播放会话")
    @PostMapping("/stop")
    public ApiResponse<MusicFlowStateDto> stop(
            @Parameter(hidden = true) @RequestHeader(value = SESSION_HEADER, required = false) String sessionHeader,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(musicFlowService.stop(resolveSessionId(sessionHeader, request)));
    }

    private String resolveSessionId(String sessionHeader, HttpServletRequest request) {
        if (sessionHeader != null && !sessionHeader.isBlank()) {
            return sessionHeader.trim();
        }
        String remoteIp = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent") == null ? "unknown" : request.getHeader("User-Agent");
        String seed = remoteIp + "|" + userAgent;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(seed.getBytes(StandardCharsets.UTF_8));
    }
}
