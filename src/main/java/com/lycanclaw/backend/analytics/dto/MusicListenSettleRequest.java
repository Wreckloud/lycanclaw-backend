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
        @Schema(description = "本次播放会话 ID", maxLength = 96)
        String listenSessionId,
        @Schema(description = "浏览器访客 ID", maxLength = 96)
        String visitorId,
        @Schema(description = "歌曲 ID", maxLength = 64)
        String songId,
        @Schema(description = "歌曲名")
        String songName,
        @Schema(description = "歌手")
        String artist,
        @Schema(description = "播放入口")
        String playbackSource,
        @Schema(description = "播放地址来源")
        String urlSource,
        @Schema(description = "开始播放时的公开页面路径")
        String pagePath,
        @Schema(description = "累计实际收听毫秒数")
        Long listenedMs,
        @Schema(description = "歌曲总时长毫秒数")
        Long durationMs,
        @Schema(description = "是否自然播放结束")
        Boolean completed
) {
}
