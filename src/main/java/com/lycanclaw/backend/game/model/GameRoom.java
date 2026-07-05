package com.lycanclaw.backend.game.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 在线对战房间聚合根。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
public class GameRoom {

    private final String roomId;
    private final GameCoreState state = new GameCoreState();
    private final List<GamePlayer> players = new ArrayList<>();
    private final List<GameLogMessage> messages = new ArrayList<>();
    private GameRoomStatus status = GameRoomStatus.WAITING;
    private Instant updatedAt;
    private int nextPlayerNumber = 1;

    public GameRoom(String roomId, Instant now) {
        this.roomId = roomId;
        this.updatedAt = now;
    }

    public String roomId() {
        return roomId;
    }

    public GameCoreState state() {
        return state;
    }

    public List<GamePlayer> players() {
        return players;
    }

    public List<GameLogMessage> messages() {
        return messages;
    }

    public GameRoomStatus status() {
        return status;
    }

    public void status(GameRoomStatus status) {
        this.status = status;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public Optional<GamePlayer> findPlayerByToken(String token) {
        return players.stream()
                .filter(player -> player.token().equals(token))
                .findFirst();
    }

    public Optional<GamePlayer> findPlayerBySide(int side) {
        return players.stream()
                .filter(player -> player.side() != null && player.side() == side)
                .findFirst();
    }

    public int nextPlayerNumber() {
        return nextPlayerNumber++;
    }

    public List<GamePlayer> sortedPlayers() {
        return players.stream()
                .sorted(Comparator
                        .comparing((GamePlayer player) -> player.side() == null ? 99 : player.side())
                        .thenComparingInt(GamePlayer::playerNumber))
                .toList();
    }
}
