package com.lycanclaw.backend.waline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.common.security.ClientIpResolver;
import com.lycanclaw.backend.common.security.InMemorySlidingWindowRateLimiter;
import com.lycanclaw.backend.waline.config.WalineProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Waline 同源代理测试。
 * 验证公开写接口边界和 OAuth 服务端过滤规则。
 * @author Wreckloud
 * @since 2026-06-24
 */
class WalineProxyControllerTest {

    private WalineProxyController controller;

    @BeforeEach
    void setUp() {
        WalineProperties properties = new WalineProperties();
        properties.setBaseUrl("http://127.0.0.1:8360");
        ClientIpResolver clientIpResolver = new ClientIpResolver();
        ReflectionTestUtils.setField(clientIpResolver, "trustForwardedHeaders", true);
        controller = new WalineProxyController(
                properties,
                clientIpResolver,
                new InMemorySlidingWindowRateLimiter(),
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(controller, "commentRateLimitPerMinute", 1);
    }

    @Test
    void rejectsCommentWithoutNicknameBeforeCallingWaline() {
        MockHttpServletRequest request = post("/waline/api/comment", "{\"comment\":\"hello\"}");

        var response = controller.proxy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).contains("称谓不能为空");
    }

    @Test
    void rejectsPublicArticleCounterWrite() {
        MockHttpServletRequest request = post("/waline/api/article", "{\"path\":\"/thoughts/a.html\"}");

        var response = controller.proxy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void keepsOnlyQqAndGithubOauthProviders() {
        String html = """
                <script>window.oauthServices = [
                  {"name":"github"},
                  {"name":"google"},
                  {"name":"qq"}
                ];</script>
                """;

        String filtered = ReflectionTestUtils.invokeMethod(controller, "filterOauthServices", html);

        assertThat(filtered).contains("github", "qq").doesNotContain("google");
    }

    @Test
    void rebuildsClientIpHeadersForWaline() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/waline/api/comment");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        request.addHeader("X-Real-IP", "10.0.0.2");
        request.addHeader("X-Forwarded-Host", "evil.example");
        request.addHeader("Host", "wreckloud.com");
        request.setScheme("https");
        request.setServerName("wreckloud.com");
        request.setServerPort(443);

        java.net.http.HttpRequest proxyRequest = ReflectionTestUtils.invokeMethod(
                controller,
                "buildProxyRequest",
                request,
                new byte[0]
        );

        assertThat(proxyRequest.headers().firstValue("X-Real-IP")).contains("203.0.113.10");
        assertThat(proxyRequest.headers().firstValue("X-Forwarded-For")).contains("203.0.113.10");
        assertThat(proxyRequest.headers().firstValue("REMOTE-HOST")).contains("203.0.113.10");
        assertThat(proxyRequest.headers().firstValue("X-Forwarded-Host")).contains("wreckloud.com");
    }

    @Test
    void rateLimitsPublicCommentByClientIp() {
        MockHttpServletRequest first = post("/waline/api/comment", "{\"nick\":\"test\",\"comment\":\"hello\"}");
        first.addHeader("X-Forwarded-For", "203.0.113.10");
        MockHttpServletRequest second = post("/waline/api/comment", "{\"nick\":\"test\",\"comment\":\"again\"}");
        second.addHeader("X-Forwarded-For", "203.0.113.10");

        ResponseEntity<byte[]> firstResponse = ReflectionTestUtils.invokeMethod(
                controller,
                "rejectUnsafePublicWrite",
                first,
                first.getContentAsByteArray()
        );
        ResponseEntity<byte[]> secondResponse = ReflectionTestUtils.invokeMethod(
                controller,
                "rejectUnsafePublicWrite",
                second,
                second.getContentAsByteArray()
        );

        assertThat(firstResponse).isNull();
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(new String(secondResponse.getBody(), StandardCharsets.UTF_8)).contains("评论过于频繁");
    }

    private MockHttpServletRequest post(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
