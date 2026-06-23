package com.lycanclaw.backend.analytics.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全站访问路径策略测试。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
class AnalyticsPathPolicyTest {

    private final AnalyticsPathPolicy policy = new AnalyticsPathPolicy();

    @Test
    void acceptsAllPublicPageKinds() {
        assertThat(new String[]{
                "/",
                "/thoughts/example.html",
                "/knowledge/Java/example.html",
                "/thoughts/",
                "/knowledge/index.html",
                "/about.html",
                "/projects/example.html",
                "/games/ultimate-tic-tac-toe.html",
                "/future-page/"
        }).allMatch(policy::isTrackable);
    }

    @Test
    void rejectsManagementApiStaticAndInvalidPaths() {
        assertThat(new String[]{
                "/admin/",
                "/api/analytics/visit/start",
                "/assets/app.js",
                "/images/logo.png",
                "/sitemap.xml",
                "/foo/%2e%2e/secret.html",
                "/foo//bar.html",
                ""
        }).noneMatch(policy::isTrackable);
    }

    @Test
    void derivesPageTypeOnlyFromPath() {
        assertThat(policy.inferPageType("/")).isEqualTo("home");
        assertThat(policy.inferPageType("/thoughts/example.html")).isEqualTo("article");
        assertThat(policy.inferPageType("/knowledge/Java/example.html")).isEqualTo("article");
        assertThat(policy.inferPageType("/games/ultimate-tic-tac-toe.html")).isEqualTo("page");
    }
}
