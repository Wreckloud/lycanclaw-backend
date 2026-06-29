package com.lycanclaw.backend.music.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.common.json.JsonNodeExtractors;
import com.lycanclaw.backend.music.dto.MusicLoginStatusDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 音乐登录服务测试。
 * 验证扫码 Cookie 保存和失效登录态清理。
 *
 * @author Wreckloud
 * @since 2026-06-24
 */
class MusicAuthServiceTest {

    private final NcmUpstreamClient upstreamClient = mock(NcmUpstreamClient.class);
    private final MusicAuthSessionService sessionService = new MusicAuthSessionService();
    private final MusicAuthService service = new MusicAuthService(
            upstreamClient,
            sessionService,
            new JsonNodeExtractors()
    );

    @Test
    void savesCookieAfterQrLoginSuccess() throws Exception {
        when(upstreamClient.get(eq("/login/qr/check"), anyMap())).thenReturn(
                new ObjectMapper().readTree("{\"code\":803,\"cookie\":\"MUSIC_U=secret\"}")
        );

        service.checkQrStatus("qr-key");

        assertThat(sessionService.getRequiredCookie()).isEqualTo("MUSIC_U=secret");
    }

    @Test
    void clearsInvalidCookieWhenStatusCheckFails() throws Exception {
        sessionService.saveCookie("expired-cookie");
        when(upstreamClient.get(eq("/login/status"), anyMap())).thenReturn(
                new ObjectMapper().readTree("{\"code\":301}")
        );

        MusicLoginStatusDto result = service.loginStatus();

        assertThat(result.loggedIn()).isFalse();
        assertThat(sessionService.hasCookie()).isFalse();
    }
}
