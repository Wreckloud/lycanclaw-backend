package com.lycanclaw.backend.game.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Locale;
import java.util.Map;

/**
 * 在线对战 WebSocket 握手拦截器。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
@Component
public class GameHandshakeInterceptor implements HandshakeInterceptor {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Value("${lycan.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        attributes.put("clientIp", resolveClientIp(request));
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    private String resolveClientIp(ServerHttpRequest request) {
        if (trustForwardedHeaders) {
            String forwardedFor = request.getHeaders().getFirst(FORWARDED_FOR_HEADER);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String first = forwardedFor.split(",")[0].trim();
                if (!first.isBlank()) {
                    return normalize(first);
                }
            }
        }

        if (request.getRemoteAddress() == null) {
            return "";
        }
        return normalize(request.getRemoteAddress().getAddress().getHostAddress());
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("::ffff:")) {
            normalized = normalized.substring("::ffff:".length());
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
