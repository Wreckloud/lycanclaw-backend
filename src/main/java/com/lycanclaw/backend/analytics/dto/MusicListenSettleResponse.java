package com.lycanclaw.backend.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 音乐收听结算结果。
 * 返回服务端保存的累计时长和完成状态。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "音乐收听结算结果")
public record MusicListenSettleResponse(
        String listenSessionId,
        long listenedMs,
        boolean completed
) {
}
