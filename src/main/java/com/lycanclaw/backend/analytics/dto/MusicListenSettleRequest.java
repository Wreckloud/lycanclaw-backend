package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 音乐收听结算请求。
 * 前端按会话提交累计播放时长，后端保留最大值以避免重复累计。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "音乐收听结算请求")
public record MusicListenSettleRequest(
        String listenSessionId,
        String visitorId,
        String songId,
        String songName,
        String artist,
        String playbackSource,
        String urlSource,
        String pagePath,
        Long listenedMs,
        Long durationMs,
        Boolean completed
) {
}
