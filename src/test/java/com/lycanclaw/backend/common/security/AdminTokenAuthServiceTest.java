package com.lycanclaw.backend.common.security;

import com.lycanclaw.backend.admin.auth.service.AdminSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理员令牌认证服务测试。
 * 验证静态应急令牌和Waline会话令牌的统一认证规则。
 * @author Wreckloud
 * @since 2026-06-24
 */
class AdminTokenAuthServiceTest {

    @Test
    void authenticatesStaticAndSessionTokens() {
        AdminSessionService sessionService = mock(AdminSessionService.class);
        AdminTokenAuthService service = new AdminTokenAuthService(sessionService);
        ReflectionTestUtils.setField(service, "staticAdminToken", "static-secret");
        AdminAuthPrincipal sessionPrincipal = new AdminAuthPrincipal(
                "session", "user-1", "Admin", "admin@example.com", "123456789",
                "administrator", "2026-06-24T20:00:00+08:00"
        );
        when(sessionService.verify("session-secret")).thenReturn(Optional.of(sessionPrincipal));

        assertThat(service.authenticate("static-secret")).contains(AdminAuthPrincipal.staticToken());
        assertThat(service.authenticate("session-secret")).contains(sessionPrincipal);
        assertThat(service.authenticate("invalid")).isEmpty();
    }
}
