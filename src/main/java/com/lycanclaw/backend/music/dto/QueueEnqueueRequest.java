package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @Description 队列入队请求模型
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Schema(description = "歌曲入队请求")
public record QueueEnqueueRequest(
        @Schema(description = "歌曲 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "2608813264")
        String id,
        @Schema(description = "请求来源标识", example = "home-random")
        String source,
        @Schema(description = "是否插入队首")
        Boolean insertFront,
        @Schema(description = "是否打断当前播放")
        Boolean interruptCurrent,
        @Schema(description = "打断时是否允许恢复被打断歌曲")
        Boolean resumeCurrent,
        @Schema(description = "优先级（越大越高）", example = "1")
        Integer priority,
        @Schema(description = "期望音质级别", example = "jymaster")
        String level,
        @Schema(description = "去重策略：replace/skip/allow", example = "replace")
        String dedupeMode
) {
}
