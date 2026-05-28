package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 队列移除请求模型
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "移除队列项请求")
public record QueueRemoveRequest(
        @Schema(description = "队列项唯一 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String queueId
) {
}
