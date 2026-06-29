package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.entity.AnalyticsVisitorIdentityEntity;
import com.lycanclaw.backend.analytics.repository.AnalyticsVisitorIdentityRepository;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 访客匿名身份服务测试。
 * 验证匿名编号只由 visitorId 决定，并保持固定展示格式。
 * @author Wreckloud
 * @since 2026-06-24
 */
class VisitorIdentityServiceTest {

    @Test
    void createsStableHashLabelFromVisitorId() {
        VisitorIdentityService service = createService();
        String first = service.displayName("visitor-1", null);
        String second = service.displayName("visitor-1", null);

        assertThat(first).matches("匿名-[0-9A-F]{8}");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void keepsSharedAnonymousLabelForMissingVisitorId() {
        VisitorIdentityService service = createService();

        assertThat(service.displayName("anonymous", null)).isEqualTo("匿名访客");
        assertThat(service.displayName("", null)).isEqualTo("匿名访客");
    }

    @Test
    void prefersVerifiedWalineNickname() {
        VisitorIdentityService service = createService();
        AnalyticsVisitorIdentityEntity identity = new AnalyticsVisitorIdentityEntity();
        identity.setNickname("已登录访客");

        assertThat(service.displayName("visitor-1", identity)).isEqualTo("已登录访客");
    }

    private VisitorIdentityService createService() {
        return new VisitorIdentityService(
                mock(AnalyticsVisitorIdentityRepository.class),
                mock(WalineGatewayClient.class),
                "Asia/Shanghai"
        );
    }
}
