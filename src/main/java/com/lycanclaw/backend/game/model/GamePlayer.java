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

    private final String token;
    private String nickname;
    private Integer side;
    private WebSocketSession session;
    private boolean connected;
    private boolean ready;
    private Instant lastSeenAt;
    private Instant readyAt;

    public GamePlayer(String token, String nickname, Instant now) {
        this.token = token;
        this.nickname = nickname;
        this.lastSeenAt = now;
    }

    public Integer side() {
        return side;
    }

    public void side(Integer side) {
        this.side = side;
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

    public boolean ready() {
        return ready;
    }

    public void ready(boolean ready) {
        this.ready = ready;
    }

    public Instant readyAt() {
        return readyAt;
    }

    public void readyAt(Instant readyAt) {
        this.readyAt = readyAt;
    }

    public boolean spectator() {
        return side == null;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }

}
