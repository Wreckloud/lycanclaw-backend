package com.lycanclaw.backend.game.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * 在线对战 WebSocket 配置。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
@Configuration
@EnableWebSocket
public class GameWebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final GameHandshakeInterceptor gameHandshakeInterceptor;

    @Value("${lycan.cors.allowed-origins}")
    private String allowedOriginsRaw;

    public GameWebSocketConfig(
            GameWebSocketHandler gameWebSocketHandler,
            GameHandshakeInterceptor gameHandshakeInterceptor
    ) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.gameHandshakeInterceptor = gameHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/api/game/ws")
                .addInterceptors(gameHandshakeInterceptor)
                .setAllowedOrigins(parseAllowedOrigins());
    }

    private String[] parseAllowedOrigins() {
        if (allowedOriginsRaw == null || allowedOriginsRaw.isBlank()) return new String[0];
        return Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }
}
