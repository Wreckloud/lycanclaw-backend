package com.lycanclaw.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 管理接口鉴权拦截器测试。
 * 验证公开登录交换限流、统一IP窗口和管理员凭证校验。
 * @author Wreckloud
 * @since 2026-06-24
 */
class AdminAuthInterceptorTest {

    private final AdminTokenAuthService adminTokenAuthService = mock(AdminTokenAuthService.class);
    private AdminAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminAuthInterceptor(
                new ClientIpResolver(),
                adminTokenAuthService,
                new InMemorySlidingWindowRateLimiter(),
                new ApiErrorResponseWriter(new ObjectMapper())
        );
        ReflectionTestUtils.setField(interceptor, "rateLimitPerMinute", 2);
        ReflectionTestUtils.setField(interceptor, "adminAuthLogEnabled", false);
    }

    @Test
    void limitsWalineExchangeWithoutRequiringExistingToken() throws Exception {
        assertThat(handle(request("POST", AdminAuthConstants.WALINE_EXCHANGE_PATH, null)).allowed()).isTrue();
        assertThat(handle(request("POST", AdminAuthConstants.WALINE_EXCHANGE_PATH, null)).allowed()).isTrue();

        InterceptorResult limited = handle(request("POST", AdminAuthConstants.WALINE_EXCHANGE_PATH, null));

        assertThat(limited.allowed()).isFalse();
        assertThat(limited.status()).isEqualTo(429);
        verifyNoInteractions(adminTokenAuthService);
    }

    @Test
    void differentAdminUrisShareSameIpWindow() throws Exception {
        AdminAuthPrincipal principal = AdminAuthPrincipal.staticToken();
        when(adminTokenAuthService.authenticate("valid")).thenReturn(Optional.of(principal));

        assertThat(handle(request("GET", "/api/admin/comments/1", "valid")).allowed()).isTrue();
        assertThat(handle(request("GET", "/api/admin/comments/2", "valid")).allowed()).isTrue();

        InterceptorResult limited = handle(request("GET", "/api/admin/comments/3", "valid"));

        assertThat(limited.allowed()).isFalse();
        assertThat(limited.status()).isEqualTo(429);
    }

    @Test
    void rejectsInvalidAdminToken() throws Exception {
        when(adminTokenAuthService.authenticate("invalid")).thenReturn(Optional.empty());

        InterceptorResult result = handle(request("GET", "/api/admin/console/summary", "invalid"));

        assertThat(result.allowed()).isFalse();
        assertThat(result.status()).isEqualTo(401);
    }

    private InterceptorResult handle(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request, response, new Object());
        return new InterceptorResult(allowed, response.getStatus());
    }

    private MockHttpServletRequest request(String method, String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        if (token != null) {
            request.addHeader(AdminAuthConstants.ADMIN_TOKEN_HEADER, token);
        }
        return request;
    }

    private record InterceptorResult(boolean allowed, int status) {
    }
}
