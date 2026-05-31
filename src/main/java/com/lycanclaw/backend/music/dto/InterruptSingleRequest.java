package com.lycanclaw.backend.music.dto;

/**
 * 打断插入单曲请求。
 * 在已有播放流中临时插入一首歌，播放结束后回归随机流。
 * @author Wreckloud
 * @since 2026-05-30
 */
public record InterruptSingleRequest(
        String songId,
        String source
) {
}
