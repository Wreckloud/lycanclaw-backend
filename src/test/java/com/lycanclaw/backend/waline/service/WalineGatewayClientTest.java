package com.lycanclaw.backend.waline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.waline.config.WalineProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Waline 网关客户端测试。
 * 验证服务端请求 Waline 时携带安全域名校验所需的来源头。
 * @author Wreckloud
 * @since 2026-07-03
 */
class WalineGatewayClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTrustedSourceHeadersToWaline() throws IOException {
        AtomicReference<String> origin = new AtomicReference<>();
        AtomicReference<String> referer = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/comment", exchange -> {
            origin.set(exchange.getRequestHeaders().getFirst("Origin"));
            referer.set(exchange.getRequestHeaders().getFirst("Referer"));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        WalineProperties properties = new WalineProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setPublicUrl("https://wreckloud.com/");

        new WalineGatewayClient(new ObjectMapper(), properties).fetchRecentComments(1);

        assertThat(origin.get()).isEqualTo("https://wreckloud.com");
        assertThat(referer.get()).isEqualTo("https://wreckloud.com/");
    }
}
