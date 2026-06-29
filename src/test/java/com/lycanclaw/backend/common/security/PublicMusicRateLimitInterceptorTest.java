package com.lycanclaw.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公开音乐接口限流测试。
 * 验证同一 IP 无法通过切换音乐接口绕过共享额度。
 *
 * @author Wreckloud
 * @since 2026-06-24
 */
class PublicMusicRateLimitInterceptorTest {

    @Test
    void sharesRateLimitAcrossMusicEndpoints() throws Exception {
        PublicMusicRateLimitInterceptor interceptor = new PublicMusicRateLimitInterceptor(
                new ClientIpResolver(),
                new InMemorySlidingWindowRateLimiter(),
                new ApiErrorResponseWriter(new ObjectMapper())
        );
        ReflectionTestUtils.setField(interceptor, "rateLimitPerMinute", 2);

        assertThat(interceptor.preHandle(
                request("/api/music/ranking/weekly"),
                new MockHttpServletResponse(),
                new Object()
        )).isTrue();
        assertThat(interceptor.preHandle(
                request("/api/music/track/detail-with-url"),
                new MockHttpServletResponse(),
                new Object()
        )).isTrue();

        MockHttpServletResponse limitedResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(
                request("/api/music/flow/start-random"),
                limitedResponse,
                new Object()
        )).isFalse();
        assertThat(limitedResponse.getStatus()).isEqualTo(429);
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
