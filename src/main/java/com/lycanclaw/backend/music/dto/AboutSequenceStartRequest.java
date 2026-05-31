package com.lycanclaw.backend.music.dto;

/**
 * 关于页顺序流启动请求。
 * 指定从前五榜单中的哪首歌曲开始顺序播放。
 * @author Wreckloud
 * @since 2026-05-30
 */
public record AboutSequenceStartRequest(
        String startSongId
) {
}
