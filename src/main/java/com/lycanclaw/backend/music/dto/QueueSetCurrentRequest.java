package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @Description 队列切换当前项请求模型
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Schema(description = "切换当前播放项请求")
public record QueueSetCurrentRequest(
        @Schema(description = "队列项唯一 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String queueId,
        @Schema(description = "是否把原 current 放回队首")
        Boolean resumeCurrent
) {
}
