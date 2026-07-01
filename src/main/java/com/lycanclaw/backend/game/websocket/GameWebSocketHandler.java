package com.lycanclaw.backend.game.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.common.security.InMemorySlidingWindowRateLimiter;
import com.lycanclaw.backend.game.dto.GameClientMessage;
import com.lycanclaw.backend.game.service.GameRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 在线对战 WebSocket 消息入口。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final GameRoomService gameRoomService;
    private final InMemorySlidingWindowRateLimiter rateLimiter;

    @Value("${lycan.game.message-rate-limit-per-minute:120}")
    private int messageRateLimitPerMinute;

    public GameWebSocketHandler(
            ObjectMapper objectMapper,
            GameRoomService gameRoomService,
            InMemorySlidingWindowRateLimiter rateLimiter
    ) {
        this.objectMapper = objectMapper;
        this.gameRoomService = gameRoomService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (!allowMessage(session)) {
            gameRoomService.sendError(session, "消息过于频繁，请稍后再试");
            return;
        }

        try {
            GameClientMessage clientMessage = objectMapper.readValue(message.getPayload(), GameClientMessage.class);
            handleClientMessage(session, clientMessage);
        } catch (JsonProcessingException ex) {
            gameRoomService.sendError(session, "消息格式错误");
        } catch (IllegalArgumentException ex) {
            gameRoomService.sendError(session, ex.getMessage());
        } catch (Exception ex) {
            log.warn("在线对战消息处理失败: sessionId={}", session.getId(), ex);
            gameRoomService.sendError(session, "在线对战暂时不可用");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        gameRoomService.disconnect(session);
    }

    private void handleClientMessage(WebSocketSession session, GameClientMessage message) {
        String type = message.type() == null ? "" : message.type().trim();
        switch (type) {
            case "join" -> gameRoomService.join(session, message.roomId(), message.playerToken(), message.nickname());
            case "move" -> gameRoomService.playMove(
                    message.roomId(),
                    message.playerToken(),
                    requireIndex(message.bigIndex(), "大棋盘位置不能为空"),
                    requireIndex(message.smallIndex(), "小棋盘位置不能为空")
            );
            case "chat" -> gameRoomService.sendChat(message.roomId(), message.playerToken(), message.text());
            case "resign" -> gameRoomService.resign(message.roomId(), message.playerToken());
            default -> throw new IllegalArgumentException("未知在线对战消息类型");
        }
    }

    private boolean allowMessage(WebSocketSession session) {
        String clientIp = String.valueOf(session.getAttributes().getOrDefault("clientIp", ""));
        return rateLimiter.allow("game-ws:" + clientIp, messageRateLimitPerMinute);
    }

    private int requireIndex(Integer value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
