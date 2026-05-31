package com.lycanclaw.backend.music.model;

/**
 * 音乐播放流模式。
 * 描述当前会话正在执行的播放策略。
 * @author Wreckloud
 * @since 2026-05-30
 */
public enum MusicFlowMode {
    IDLE("idle"),
    RANDOM("random"),
    ABOUT_SEQUENCE("about-sequence"),
    INTERRUPT_SINGLE("interrupt-single");

    private final String value;

    MusicFlowMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
