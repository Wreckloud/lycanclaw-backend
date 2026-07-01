package com.lycanclaw.backend.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.game.dto.CreateGameRoomResponse;
import com.lycanclaw.backend.game.dto.GamePlayerSnapshot;
import com.lycanclaw.backend.game.dto.GameRoomSnapshot;
import com.lycanclaw.backend.game.dto.GameServerMessage;
import com.lycanclaw.backend.game.dto.GameStateSnapshot;
import com.lycanclaw.backend.game.model.GameCoreState;
import com.lycanclaw.backend.game.model.GameLogMessage;
import com.lycanclaw.backend.game.model.GameMove;
import com.lycanclaw.backend.game.model.GamePlayer;
import com.lycanclaw.backend.game.model.GameRecordedMove;
import com.lycanclaw.backend.game.model.GameRoom;
import com.lycanclaw.backend.game.model.GameRoomStatus;
import com.lycanclaw.backend.game.model.GameRuleEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycanclaw.backend.game.model.GameConstants.EMPTY;
import static com.lycanclaw.backend.game.model.GameConstants.O;
import static com.lycanclaw.backend.game.model.GameConstants.X;

/**
 * 在线对战房间服务。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
@Service
public class GameRoomService {

    private static final int MAX_NICKNAME_LENGTH = 16;
    private static final int MAX_CHAT_LENGTH = 200;
    private static final int MAX_MESSAGE_COUNT = 200;
    private static final Duration WAITING_ROOM_TTL = Duration.ofHours(2);
    private static final Duration FINISHED_ROOM_TTL = Duration.ofMinutes(30);
    private static final String ROOM_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final GameRulesService gameRulesService;
    private final ObjectMapper objectMapper;

    public GameRoomService(GameRulesService gameRulesService, ObjectMapper objectMapper) {
        this.gameRulesService = gameRulesService;
        this.objectMapper = objectMapper;
    }

    public CreateGameRoomResponse createRoom(String nickname) {
        Instant now = Instant.now();
        String cleanNickname = normalizeNickname(nickname);
        String roomId = createUniqueRoomId();
        String playerToken = createPlayerToken();
        GameRoom room = new GameRoom(roomId, now);
        room.players().add(new GamePlayer(X, playerToken, cleanNickname, now));
        appendSystemMessage(room, cleanNickname + " 创建了房间");
        rooms.put(roomId, room);
        return new CreateGameRoomResponse(roomId, playerToken);
    }

    public void join(WebSocketSession session, String roomId, String playerToken, String nickname) {
        GameRoom room = findRoom(roomId);
        List<OutboundMessage> messages;
        synchronized (room) {
            Instant now = Instant.now();
            GamePlayer player = resolveJoiningPlayer(room, playerToken, nickname, now);
            reconnectPlayer(session, player, now);
            session.getAttributes().put("gameRoomId", room.roomId());
            session.getAttributes().put("gamePlayerToken", player.token());
            room.touch(now);

            if (room.status() == GameRoomStatus.WAITING && room.players().size() == 2) {
                startRoom(room);
            }
            messages = snapshotMessages(room);
        }
        sendAll(messages);
    }

    public void playMove(String roomId, String playerToken, int bigIndex, int smallIndex) {
        GameRoom room = findRoom(roomId);
        List<OutboundMessage> messages;
        synchronized (room) {
            GamePlayer player = requirePlayer(room, playerToken);
            if (room.status() != GameRoomStatus.PLAYING) {
                throw new IllegalArgumentException("当前房间不能落子");
            }
            if (room.state().currentPlayer() != player.side()) {
                throw new IllegalArgumentException("还没轮到你落子");
            }

            GameMove move = new GameMove(bigIndex, smallIndex);
            if (!gameRulesService.applyMove(room.state(), move)) {
                throw new IllegalArgumentException("非法落子");
            }

            appendMoveMessages(room, player, move);
            if (room.state().winner() != EMPTY) {
                room.status(GameRoomStatus.FINISHED);
                appendSystemMessage(room, getPlayerName(room, room.state().winner()) + " 赢得本局。");
            }
            room.touch(Instant.now());
            messages = snapshotMessages(room);
        }
        sendAll(messages);
    }

    public void sendChat(String roomId, String playerToken, String text) {
        GameRoom room = findRoom(roomId);
        List<OutboundMessage> messages;
        synchronized (room) {
            GamePlayer player = requirePlayer(room, playerToken);
            if (room.status() == GameRoomStatus.FINISHED) {
                throw new IllegalArgumentException("对局已结束，不能继续聊天");
            }
            String cleanText = normalizeChatText(text);
            appendChatMessage(room, player, cleanText);
            room.touch(Instant.now());
            messages = snapshotMessages(room);
        }
        sendAll(messages);
    }

    public void resign(String roomId, String playerToken) {
        GameRoom room = findRoom(roomId);
        List<OutboundMessage> messages;
        synchronized (room) {
            GamePlayer loser = requirePlayer(room, playerToken);
            if (room.status() != GameRoomStatus.PLAYING) {
                throw new IllegalArgumentException("当前房间不能投降");
            }

            gameRulesService.resign(room.state(), loser.side());
            room.status(GameRoomStatus.FINISHED);
            appendSystemMessage(room, getPlayerName(room, loser.side()) + " 投降\n" + getPlayerName(room, room.state().winner()) + " 赢得本局。");
            room.touch(Instant.now());
            messages = snapshotMessages(room);
        }
        sendAll(messages);
    }

    public void disconnect(WebSocketSession session) {
        Object roomId = session.getAttributes().get("gameRoomId");
        Object playerToken = session.getAttributes().get("gamePlayerToken");
        if (!(roomId instanceof String roomIdValue) || !(playerToken instanceof String playerTokenValue)) {
            return;
        }

        GameRoom room = rooms.get(roomIdValue);
        if (room == null) return;

        List<OutboundMessage> messages;
        synchronized (room) {
            room.findPlayerByToken(playerTokenValue).ifPresent(player -> {
                if (session.getId().equals(player.session() == null ? null : player.session().getId())) {
                    player.connected(false);
                    player.session(null);
                    player.touch(Instant.now());
                }
            });
            room.touch(Instant.now());
            messages = snapshotMessages(room);
        }
        sendAll(messages);
    }

    @Scheduled(fixedDelay = 600_000L)
    public void cleanupExpiredRooms() {
        Instant now = Instant.now();
        rooms.entrySet().removeIf(entry -> {
            GameRoom room = entry.getValue();
            synchronized (room) {
                Duration age = Duration.between(room.updatedAt(), now);
                boolean allDisconnected = room.players().stream().noneMatch(GamePlayer::connected);
                if (room.status() == GameRoomStatus.FINISHED) {
                    return age.compareTo(FINISHED_ROOM_TTL) > 0 && allDisconnected;
                }
                return age.compareTo(WAITING_ROOM_TTL) > 0 && allDisconnected;
            }
        });
    }

    public Optional<GameRoomSnapshot> snapshotForPlayer(String roomId, String playerToken) {
        GameRoom room = rooms.get(normalizeRoomId(roomId));
        if (room == null) return Optional.empty();
        synchronized (room) {
            return Optional.of(createSnapshot(room, requirePlayer(room, playerToken).side()));
        }
    }

    private GamePlayer resolveJoiningPlayer(GameRoom room, String playerToken, String nickname, Instant now) {
        String cleanToken = normalizePlayerToken(playerToken);
        String cleanNickname = normalizeNickname(nickname);
        Optional<GamePlayer> existing = room.findPlayerByToken(cleanToken);
        if (existing.isPresent()) {
            GamePlayer player = existing.get();
            player.nickname(cleanNickname);
            return player;
        }

        if (room.status() != GameRoomStatus.WAITING || room.players().size() >= 2) {
            throw new IllegalArgumentException("房间已满或对局已开始");
        }

        GamePlayer player = new GamePlayer(O, cleanToken, cleanNickname, now);
        room.players().add(player);
        appendSystemMessage(room, cleanNickname + " 加入了游戏");
        return player;
    }

    private void reconnectPlayer(WebSocketSession session, GamePlayer player, Instant now) {
        WebSocketSession oldSession = player.session();
        if (oldSession != null && oldSession.isOpen() && !oldSession.getId().equals(session.getId())) {
            closeQuietly(oldSession);
        }
        player.session(session);
        player.connected(true);
        player.touch(now);
    }

    private void startRoom(GameRoom room) {
        room.status(GameRoomStatus.PLAYING);
        room.state().started(true);
        room.state().currentPlayer(X);
        room.state().nextBoard(null);
        appendSystemMessage(room, "在线对战\n" + getPlayerName(room, X) + " 先手");
    }

    private void appendMoveMessages(GameRoom room, GamePlayer player, GameMove move) {
        String text = getPlayerName(room, player.side())
                + " 下在 " + gameRulesService.formatBigBoardIndex(move.bigIndex())
                + " 棋盘 " + gameRulesService.formatSmallCellPosition(move.smallIndex());
        appendMessage(room, "move", player.side(), null, null, text);

        for (GameRuleEvent event : room.state().lastRuleEvents()) {
            appendSettlementMessage(room, event);
        }
    }

    private void appendSettlementMessage(GameRoom room, GameRuleEvent event) {
        String boardText = gameRulesService.formatBigBoardIndex(event.boardIndex()) + " 棋盘";
        if (!gameRulesService.isPlayer(event.owner())) {
            appendSystemMessage(room, boardText + " 满盘结算\n无人控制，不触发入口奖励。");
            return;
        }

        String ownerName = getPlayerName(room, event.owner());
        String nextName = getPlayerName(room, gameRulesService.opponent(event.owner()));
        appendSystemMessage(room, boardText + " 满盘结算\n由于 " + ownerName + " 控制了 " + boardText
                + "，填充 " + event.filledCount() + " 格入口\n轮到 " + nextName + " 自由落子。");
    }

    private void appendChatMessage(GameRoom room, GamePlayer player, String text) {
        appendMessage(room, "chat", null, player.side(), player.nickname(), player.nickname() + " 说：" + text);
    }

    private void appendSystemMessage(GameRoom room, String text) {
        appendMessage(room, "system", null, null, null, text);
    }

    private void appendMessage(GameRoom room, String type, Integer player, Integer sender, String senderName, String text) {
        room.messages().add(new GameLogMessage(
                "game-" + System.currentTimeMillis() + "-" + room.messages().size(),
                type,
                player,
                sender,
                senderName,
                text,
                System.currentTimeMillis()
        ));
        while (room.messages().size() > MAX_MESSAGE_COUNT) {
            room.messages().remove(0);
        }
    }

    private List<OutboundMessage> snapshotMessages(GameRoom room) {
        List<OutboundMessage> messages = new ArrayList<>();
        for (GamePlayer player : room.players()) {
            WebSocketSession session = player.session();
            if (session == null || !session.isOpen()) continue;
            messages.add(new OutboundMessage(session, GameServerMessage.snapshot(createSnapshot(room, player.side()))));
        }
        return messages;
    }

    private GameRoomSnapshot createSnapshot(GameRoom room, Integer selfSide) {
        GameCoreState state = room.state();
        return new GameRoomSnapshot(
                room.roomId(),
                room.status(),
                selfSide,
                room.sortedPlayers().stream()
                        .map(player -> new GamePlayerSnapshot(player.side(), player.nickname(), player.connected()))
                        .toList(),
                new GameStateSnapshot(
                        state.board().clone(),
                        state.smallBoardStatus().clone(),
                        state.smallBoardResolved().clone(),
                        copyWinningLines(state.smallBoardWinningLines()),
                        state.currentPlayer(),
                        state.nextBoard(),
                        state.winner(),
                        copyLine(state.bigBoardWinningLine()),
                        state.started(),
                        List.copyOf(state.moveHistory()),
                        List.copyOf(state.lastTurnMoves()),
                        List.copyOf(state.lastRuleEvents())
                ),
                List.copyOf(room.messages())
        );
    }

    private List<List<Integer>> copyWinningLines(List<List<Integer>> lines) {
        return lines.stream()
                .map(this::copyLine)
                .toList();
    }

    private List<Integer> copyLine(List<Integer> line) {
        return line == null ? null : List.copyOf(line);
    }

    private GameRoom findRoom(String roomId) {
        GameRoom room = rooms.get(normalizeRoomId(roomId));
        if (room == null) {
            throw new IllegalArgumentException("房间不存在或已过期");
        }
        return room;
    }

    private GamePlayer requirePlayer(GameRoom room, String playerToken) {
        String cleanToken = normalizePlayerToken(playerToken);
        return room.findPlayerByToken(cleanToken)
                .orElseThrow(() -> new IllegalArgumentException("玩家不属于当前房间"));
    }

    private String getPlayerName(GameRoom room, int player) {
        return room.findPlayerBySide(player)
                .map(GamePlayer::nickname)
                .orElseGet(() -> player == X ? "蓝方 X" : "红方 O");
    }

    private String normalizeRoomId(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("房间 ID 不能为空");
        }
        return roomId.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePlayerToken(String playerToken) {
        if (playerToken == null || playerToken.isBlank() || playerToken.length() > 96) {
            throw new IllegalArgumentException("玩家凭证无效");
        }
        return playerToken.trim();
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        String cleanNickname = nickname.trim();
        if (cleanNickname.isBlank()) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        if (cleanNickname.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("昵称不能超过 16 个字");
        }
        return cleanNickname;
    }

    private String normalizeChatText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("聊天内容不能为空");
        }
        String cleanText = text.trim();
        if (cleanText.isBlank()) {
            throw new IllegalArgumentException("聊天内容不能为空");
        }
        if (cleanText.length() > MAX_CHAT_LENGTH) {
            throw new IllegalArgumentException("聊天内容不能超过 200 字");
        }
        return cleanText;
    }

    private String createUniqueRoomId() {
        for (int i = 0; i < 10; i++) {
            String roomId = randomRoomId();
            if (!rooms.containsKey(roomId)) {
                return roomId;
            }
        }
        throw new IllegalStateException("创建房间失败，请稍后重试");
    }

    private String randomRoomId() {
        StringBuilder builder = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            builder.append(ROOM_ID_ALPHABET.charAt(secureRandom.nextInt(ROOM_ID_ALPHABET.length())));
        }
        return builder.toString();
    }

    private String createPlayerToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendAll(List<OutboundMessage> messages) {
        for (OutboundMessage message : messages) {
            send(message.session(), message.payload());
        }
    }

    public void sendError(WebSocketSession session, String message) {
        send(session, GameServerMessage.error(message));
    }

    private void send(WebSocketSession session, GameServerMessage message) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException ex) {
            closeQuietly(session);
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.NORMAL);
        } catch (IOException ignored) {
            // 断开失败不影响房间状态，下次心跳或重连会覆盖旧连接。
        }
    }

    private record OutboundMessage(WebSocketSession session, GameServerMessage payload) {
    }
}
