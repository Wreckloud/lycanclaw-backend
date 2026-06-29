package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.MusicListenSettleRequest;
import com.lycanclaw.backend.analytics.entity.MusicListenSessionEntity;
import com.lycanclaw.backend.analytics.repository.MusicListenSessionRepository;
import com.lycanclaw.backend.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 音乐收听结算服务测试。
 * 验证访客边界、累计时长约束和会话碰撞保护。
 *
 * @author Wreckloud
 * @since 2026-06-24
 */
class MusicListenAnalyticsServiceTest {

    private final MusicListenSessionRepository repository = mock(MusicListenSessionRepository.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    private final MusicListenAnalyticsService service = new MusicListenAnalyticsService(
            repository,
            clientIpResolver,
            new AnalyticsPathPolicy(),
            "Asia/Shanghai"
    );

    @BeforeEach
    void setUp() {
        when(clientIpResolver.resolve(servletRequest)).thenReturn("203.0.113.10");
        when(servletRequest.getHeader("User-Agent")).thenReturn("test-agent");
    }

    @Test
    void createsSessionAndCapsListenedTimeAtDuration() {
        when(repository.findByListenSessionId("listen-session-1")).thenReturn(Optional.empty());
        MusicListenSettleRequest request = request("listen-session-1", "visitor-1", "100", "/about.html");

        service.settle(request, servletRequest);

        ArgumentCaptor<MusicListenSessionEntity> captor = ArgumentCaptor.forClass(MusicListenSessionEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getListenedMs()).isEqualTo(60_000);
        assertThat(captor.getValue().getDurationMs()).isEqualTo(60_000);
        assertThat(captor.getValue().isCompleted()).isTrue();
        assertThat(captor.getValue().getPagePath()).isEqualTo("/about.html");
    }

    @Test
    void rejectsMissingVisitorId() {
        MusicListenSettleRequest request = request("listen-session-1", "", "100", "/about.html");

        assertThatThrownBy(() -> service.settle(request, servletRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("visitorId");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsSessionIdCollision() {
        MusicListenSessionEntity existing = new MusicListenSessionEntity();
        existing.setListenSessionId("listen-session-1");
        existing.setVisitorId("visitor-original");
        existing.setSongId("100");
        existing.setListenedMs(1_000);
        existing.setDurationMs(60_000);
        when(repository.findByListenSessionId("listen-session-1")).thenReturn(Optional.of(existing));

        MusicListenSettleRequest request = request("listen-session-1", "visitor-other", "100", "/about.html");

        assertThatThrownBy(() -> service.settle(request, servletRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("其他播放会话");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsReservedPagePath() {
        MusicListenSettleRequest request = request("listen-session-1", "visitor-1", "100", "/admin/index.html");

        assertThatThrownBy(() -> service.settle(request, servletRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("公开页面");
    }

    private MusicListenSettleRequest request(String sessionId, String visitorId, String songId, String pagePath) {
        return new MusicListenSettleRequest(
                sessionId,
                visitorId,
                songId,
                "测试歌曲",
                "测试歌手",
                "home-random",
                "public",
                pagePath,
                120_000L,
                60_000L,
                true
        );
    }
}
