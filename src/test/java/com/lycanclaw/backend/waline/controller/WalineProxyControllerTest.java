package com.lycanclaw.backend.waline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.waline.config.WalineProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
        controller = new WalineProxyController(properties, new ObjectMapper());
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

    private MockHttpServletRequest post(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
