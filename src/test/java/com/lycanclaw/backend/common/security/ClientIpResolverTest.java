package com.lycanclaw.backend.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户端IP解析器测试。
 * 验证直连地址与受信代理头的明确边界。
 * @author Wreckloud
 * @since 2026-06-24
 */
class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeaderWhenTrustIsDisabled() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = request();

        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void readsFirstForwardedIpWhenTrustIsEnabled() {
        ClientIpResolver resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(resolver, "trustForwardedHeaders", true);
        MockHttpServletRequest request = request();

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.2");
        return request;
    }
}
