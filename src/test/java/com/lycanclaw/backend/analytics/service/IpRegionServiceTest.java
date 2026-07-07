package com.lycanclaw.backend.analytics.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IP 地区解析服务测试。
 * 验证 ip2region 原始分隔符格式会被整理为后台可读文本。
 *
 * @author Wreckloud
 * @since 2026-07-07
 */
class IpRegionServiceTest {

    private final IpRegionService service = new IpRegionService("", "");

    @Test
    void formatsIp2RegionRawTextForAdminDisplay() {
        assertThat(service.format("中国|四川省|成都市|联通")).isEqualTo("四川省 成都市 中国联通");
        assertThat(service.format("中国|四川省|达州市|电信")).isEqualTo("四川省 达州市 中国电信");
    }

    @Test
    void keepsAlreadyFormattedRegionReadable() {
        assertThat(service.format("四川省 成都市 中国联通3GNET网络"))
                .isEqualTo("四川省 成都市 中国联通3GNET网络");
    }
}
