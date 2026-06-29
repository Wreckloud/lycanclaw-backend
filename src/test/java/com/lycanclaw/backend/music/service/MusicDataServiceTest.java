package com.lycanclaw.backend.music.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.common.exception.UpstreamServiceException;
import com.lycanclaw.backend.common.json.JsonNodeExtractors;
import com.lycanclaw.backend.common.security.InMemorySlidingWindowRateLimiter;
import com.lycanclaw.backend.music.config.MusicProperties;
import com.lycanclaw.backend.music.dto.MusicTrackLyricDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 音乐数据服务测试。
 * 验证排行榜缓存、隐私字段和歌曲参数校验。
 *
 * @author Wreckloud
 * @since 2026-06-24
 */
class MusicDataServiceTest {

    private final NcmUpstreamClient upstreamClient = mock(NcmUpstreamClient.class);
    private final MusicProperties properties = new MusicProperties();
    private MusicDataService service;

    @BeforeEach
    void setUp() {
        properties.setRankingOwnerUid("123456");
        properties.setPreferredLevel("exhigh");
        service = new MusicDataService(
                upstreamClient,
                new MusicAuthSessionService(),
                new JsonNodeExtractors(),
                new InMemorySlidingWindowRateLimiter(),
                properties
        );
    }

    @Test
    void cachesWeeklyRankingWithoutExposingOwnerUid() throws Exception {
        when(upstreamClient.get(eq("/user/record"), anyMap())).thenReturn(
                new ObjectMapper().readTree("""
                        {
                          "code": 200,
                          "weekData": [
                            {"song": {"id": 1, "name": "歌曲一", "ar": [{"name": "歌手"}], "al": {"picUrl": "cover"}}},
                            {"song": {"id": 2, "name": "歌曲二", "ar": [{"name": "歌手"}], "al": {"picUrl": "cover"}}}
                          ]
                        }
                        """
                )
        );

        Map<String, Object> first = service.getWeeklyRanking(1);
        Map<String, Object> second = service.getWeeklyRanking(2);

        assertThat(first).doesNotContainKey("uid");
        assertThat((Iterable<?>) first.get("tracks")).hasSize(1);
        assertThat((Iterable<?>) second.get("tracks")).hasSize(2);
        verify(upstreamClient, times(1)).get(eq("/user/record"), anyMap());
    }

    @Test
    void rejectsInvalidSongIdBeforeCallingUpstream() {
        assertThatThrownBy(() -> service.getTrackDetailWithUrl("not-a-song", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("网易云歌曲 ID");

        verifyNoInteractions(upstreamClient);
    }

    @Test
    void rejectsUnsupportedQualityBeforeCallingUpstream() {
        assertThatThrownBy(() -> service.getTrackDetailWithUrl("100", "unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("音质级别");

        verifyNoInteractions(upstreamClient);
    }

    @Test
    void fallsBackToNewLyricEndpointWhenPrimaryReturns404() throws Exception {
        when(upstreamClient.get(eq("/lyric"), anyMap()))
                .thenThrow(new UpstreamServiceException("上游音乐服务请求失败，状态码: 404"));
        when(upstreamClient.get(eq("/lyric/new"), anyMap()))
                .thenReturn(new ObjectMapper().readTree("""
                        {
                          "lrc": {
                            "lyric": "[00:01.00]第一句\\n[00:02.50]第二句"
                          }
                        }
                        """
                ));

        MusicTrackLyricDto lyric = service.getTrackLyric("100");

        assertThat(lyric.hasLyric()).isTrue();
        assertThat(lyric.lines()).hasSize(2);
        assertThat(lyric.lines().get(0).timeMs()).isEqualTo(1_000L);
        assertThat(lyric.lines().get(1).text()).isEqualTo("第二句");
        verify(upstreamClient).get(eq("/lyric"), anyMap());
        verify(upstreamClient).get(eq("/lyric/new"), anyMap());
    }
}
