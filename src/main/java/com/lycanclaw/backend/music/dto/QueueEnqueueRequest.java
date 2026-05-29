package com.lycanclaw.backend.music.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * QueueEnqueueRequest：
 * QueueEnqueueRequest的数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "歌曲入队请求")
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueueEnqueueRequest(
        @Schema(description = "歌曲 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "2608813264")
        String id,
        @Schema(description = "请求来源标识", example = "home-random")
        String source,
        @Schema(description = "期望音质级别", example = "exhigh")
        String level
) {
}
