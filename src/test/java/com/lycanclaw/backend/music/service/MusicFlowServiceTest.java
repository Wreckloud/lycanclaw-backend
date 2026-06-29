package com.lycanclaw.backend.music.service;

import com.lycanclaw.backend.music.dto.MusicFlowStateDto;
import com.lycanclaw.backend.music.dto.MusicTrackDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 音乐播放流服务测试。
 * 验证随机流、关于页顺序流、单曲打断和会话参数边界。
 *
 * @author Wreckloud
 * @since 2026-06-24
 */
class MusicFlowServiceTest {

    private static final String SESSION_ID = "lycan-session-123456";

    private final MusicDataService musicDataService = mock(MusicDataService.class);
    private final MusicFlowService service = new MusicFlowService(musicDataService);

    @BeforeEach
    void setUp() {
        when(musicDataService.getTrackDetailWithUrl(org.mockito.ArgumentMatchers.anyString(), isNull()))
                .thenAnswer(invocation -> playableTrack(invocation.getArgument(0)));
    }

    @Test
    void startsRandomFlowAndReturnsToItAfterInterrupt() {
        when(musicDataService.getHomeTrackPool(anyInt()))
                .thenReturn(List.of(track("100", "随机歌曲")));

        MusicFlowStateDto started = service.startRandom(SESSION_ID);
        MusicFlowStateDto interrupted = service.interruptSingle(SESSION_ID, "200", "article-embed");
        MusicFlowStateDto resumed = service.next(SESSION_ID);

        assertThat(started.mode()).isEqualTo("random");
        assertThat(started.current().id()).isEqualTo("100");
        assertThat(interrupted.mode()).isEqualTo("interrupt-single");
        assertThat(interrupted.current().id()).isEqualTo("200");
        assertThat(resumed.mode()).isEqualTo("random");
        assertThat(resumed.current().id()).isEqualTo("100");
    }

    @Test
    void playsAboutRankingInOrderThenStops() {
        when(musicDataService.getAboutRankingTracks(anyInt())).thenReturn(List.of(
                track("10", "第一首"),
                track("11", "第二首"),
                track("12", "第三首")
        ));

        MusicFlowStateDto started = service.startAboutSequence(SESSION_ID, "11");
        MusicFlowStateDto next = service.next(SESSION_ID);
        MusicFlowStateDto finished = service.next(SESSION_ID);

        assertThat(started.current().id()).isEqualTo("11");
        assertThat(next.current().id()).isEqualTo("12");
        assertThat(finished.mode()).isEqualTo("idle");
        assertThat(finished.current()).isNull();
    }

    @Test
    void skipsUnavailableSongsInsideRandomPool() {
        when(musicDataService.getHomeTrackPool(anyInt()))
                .thenReturn(List.of(track("100", "不可播放"), track("101", "可播放")));
        when(musicDataService.getTrackDetailWithUrl("100", null)).thenReturn(Map.of(
                "id", "100",
                "name", "不可播放",
                "artist", "歌手",
                "cover", "",
                "url", "",
                "source", "public_only"
        ));

        MusicFlowStateDto result = service.startRandom(SESSION_ID);

        assertThat(result.current().id()).isEqualTo("101");
    }

    @Test
    void rejectsInvalidSessionAndInterruptSource() {
        assertThatThrownBy(() -> service.startRandom("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("播放会话格式无效");
        assertThatThrownBy(() -> service.interruptSingle(SESSION_ID, "100", "unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("播放来源");
    }

    private MusicTrackDto track(String id, String name) {
        return new MusicTrackDto(id, name, "歌手", "");
    }

    private Map<String, Object> playableTrack(String id) {
        return Map.of(
                "id", id,
                "name", "歌曲-" + id,
                "artist", "歌手",
                "cover", "https://example.com/cover.jpg",
                "url", "https://example.com/" + id + ".mp3",
                "source", "public"
        );
    }
}
