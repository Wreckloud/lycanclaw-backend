package com.lycanclaw.backend.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 关于页顺序流启动请求。
 * 指定从前五榜单中的哪首歌曲开始顺序播放。
 * @author Wreckloud
 * @since 2026-05-30
 */
@Schema(description = "关于页顺序播放请求")
public record AboutSequenceStartRequest(
        @Schema(description = "开始播放的歌曲 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String startSongId
) {
}
