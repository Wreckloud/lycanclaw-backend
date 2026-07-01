package com.lycanclaw.backend.game.model;

import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;

/**
 * 在线对战玩家。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public class GamePlayer {

    private final int side;
    private final String token;
    private String nickname;
    private WebSocketSession session;
    private boolean connected;
    private Instant lastSeenAt;

    public GamePlayer(int side, String token, String nickname, Instant now) {
        this.side = side;
        this.token = token;
        this.nickname = nickname;
        this.lastSeenAt = now;
    }

    public int side() {
        return side;
    }

    public String token() {
        return token;
    }

    public String nickname() {
        return nickname;
    }

    public void nickname(String nickname) {
        this.nickname = nickname;
    }

    public WebSocketSession session() {
        return session;
    }

    public void session(WebSocketSession session) {
        this.session = session;
    }

    public boolean connected() {
        return connected;
    }

    public void connected(boolean connected) {
        this.connected = connected;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }
}
