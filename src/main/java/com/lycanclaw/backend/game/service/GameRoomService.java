package com.lycanclaw.backend.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.game.dto.CreateGameRoomResponse;
import com.lycanclaw.backend.game.dto.GamePlayerSnapshot;
import com.lycanclaw.backend.game.dto.GameRoomListItem;
import com.lycanclaw.backend.game.dto.GameRoomSnapshot;
import com.lycanclaw.backend.game.dto.GameServerMessage;
import com.lycanclaw.backend.game.dto.GameStateSnapshot;
import com.lycanclaw.backend.game.model.GameCoreState;
import com.lycanclaw.backend.game.model.GameLogMessage;
import com.lycanclaw.backend.game.model.GameMove;
import com.lycanclaw.backend.game.model.GamePlayer;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycanclaw.backend.game.model.GameConstants.DRAW;
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
    private static final int MAX_PLAYER_COUNT = 4;
    private static final int READY_TO_START_COUNT = 2;
    private static final Duration WAITING_ROOM_TTL = Duration.ofHours(2);
    private static final Duration FINISHED_ROOM_TTL = Duration.ofMinutes(30);
    private static final String ROOM_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String EVENT_ROOM_CREATED = "ROOM_CREATED";
    private static final String EVENT_PLAYER_JOINED = "PLAYER_JOINED";
    private static final String EVENT_PLAYER_LEFT = "PLAYER_LEFT";
    private static final String EVENT_PLAYER_READY = "PLAYER_READY";
    private static final String EVENT_PLAYER_UNREADY = "PLAYER_UNREADY";
    private static final String EVENT_GAME_STARTED = "GAME_STARTED";
    private static final String EVENT_MOVE_PLAYED = "MOVE_PLAYED";
    private static final String EVENT_BOARD_DRAW = "BOARD_DRAW";
    private static final String EVENT_BOARD_SETTLEMENT = "BOARD_SETTLEMENT";
    private static final String EVENT_GAME_DRAW = "GAME_DRAW";
    private static final String EVENT_LINE_WIN = "LINE_WIN";
    private static final String EVENT_RESIGN_WIN = "RESIGN_WIN";
    private static final String EVENT_LEAVE_WIN = "LEAVE_WIN";
    private static final String EVENT_CHAT_SENT = "CHAT_SENT";

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
        room.players().add(new GamePlayer(playerToken, cleanNickname, now));
        appendSystemEvent(room, EVENT_ROOM_CREATED, eventData(
                "nickname", cleanNickname,
                "roomId", roomId
        ));
        rooms.put(roomId, room);
        return new CreateGameRoomResponse(roomId, playerToken);
    }

    public List<GameRoomListItem> listRooms() {
        return rooms.values().stream()
                .map(this::createRoomListItem)
                .sorted(Comparator.comparing(GameRoomListItem::updatedAt).reversed())
                .toList();
    }

    public void join(WebSocketSession session, String roomId, String playerToken, String nickname) {
        GameRoom room = findRoom(roomId);
        List<OutboundMessage> messages;
        synchronized (room) {
            Instant now = Instant.now();
            GamePlayer player = resolveJoiningPlayer(room, playerToken, nickname, now);
            boolean wasDisconnected = !player.connected();
            boolean shouldAnnounceJoin = wasDisconnected && player.lastSeenAt().isAfter(room.updatedAt());
            reconnectPlayer(session, player, now);
            if (shouldAnnounceJoin) {
                appendSystemEvent(room, EVENT_PLAYER_JOINED, eventData("nickname", player.nickname()));
            }
            session.getAttributes().put("gameRoomId", room.roomId());
            session.getAttributes().put("gamePlayerToken", player.token());
            room.touch(now);
            messages = snapshotMessages(room);
        }
        sendAll(messages);
    }

    public void leave(WebSocketSession session, String roomId, String playerToken) {
        GameRoom room = findRoom(roomId);
        List<OutboundMessage> messages;
        synchronized (room) {
            GamePlayer player = requirePlayer(room, playerToken);
            handlePlayerExit(room, player, Instant.now());
            session.getAttributes().remove("gameRoomId");
            session.getAttributes().remove("gamePlayerToken");
            messages = snapshotMessages(room);
        }
        removeRoomIfEmpty(room);
        sendAll(messages);
    }

    public void markReady(String roomId, String playerToken) {
        GameRoom room = findRoom(roomId);
        List<OutboundMessage> messages;
        synchronized (room) {
            GamePlayer player = requirePlayer(room, playerToken);
            if (room.status() != GameRoomStatus.WAITING && room.status() != GameRoomStatus.FINISHED) {
                throw new IllegalArgumentException("当前房间不能准备");
            }

            if (player.ready()) {
                player.ready(false);
                player.readyAt(null);
                appendSystemEvent(room, EVENT_PLAYER_UNREADY, eventData("nickname", player.nickname()));
            } else {
                player.ready(true);
                player.readyAt(Instant.now());
                appendSystemEvent(room, EVENT_PLAYER_READY, eventData("nickname", player.nickname()));
            }
            if (readyPlayers(room).size() >= READY_TO_START_COUNT) {
                startRoom(room);
            }
            room.touch(Instant.now());
            messages = snapshotMessages(room);
        }
        sendAll(messages);
    }

    public void playMove(String roomId, String playerToken, int bigIndex, int smallIndex) {
        GameRoom room = findRoom(roomId);
        List<OutboundMessage> messages;
        synchronized (room) {
            GamePlayer player = requirePlayer(room, playerToken);
            Integer playerSide = requireActiveSide(player, "观战者不能落子");
            if (room.status() != GameRoomStatus.PLAYING) {
                throw new IllegalArgumentException("当前房间不能落子");
            }
            if (room.state().currentPlayer() != playerSide) {
                throw new IllegalArgumentException("还没轮到你落子");
            }

            GameMove move = new GameMove(bigIndex, smallIndex);
            if (!gameRulesService.applyMove(room.state(), move)) {
                throw new IllegalArgumentException("非法落子");
            }

            appendMoveMessages(room, playerSide, move);
            if (room.state().winner() != EMPTY) {
                finishRoomByBoardResult(room);
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
            Integer loserSide = requireActiveSide(loser, "观战者不能投降");
            if (room.status() != GameRoomStatus.PLAYING) {
                throw new IllegalArgumentException("当前房间不能投降");
            }

            gameRulesService.resign(room.state(), loserSide);
            room.status(GameRoomStatus.FINISHED);
            resetReady(room);
            appendSystemEvent(room, EVENT_RESIGN_WIN, eventData(
                    "loser", loserSide,
                    "winner", room.state().winner()
            ));
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
                    handlePlayerExit(room, player, Instant.now());
                }
            });
            room.touch(Instant.now());
            messages = snapshotMessages(room);
        }
        removeRoomIfEmpty(room);
        sendAll(messages);
    }

    @Scheduled(fixedDelay = 10_000L)
    public void cleanupExpiredRooms() {
        Instant now = Instant.now();
        List<OutboundMessage> messages = new ArrayList<>();
        for (GameRoom room : rooms.values()) {
            synchronized (room) {
                messages.addAll(snapshotMessages(room));
            }
        }
        sendAll(messages);

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
            GamePlayer player = requirePlayer(room, playerToken);
            return Optional.of(createSnapshot(room, player.side(), player.token()));
        }
    }

    private GameRoomListItem createRoomListItem(GameRoom room) {
        synchronized (room) {
            int playerCount = room.players().size();
            int readyCount = readyPlayers(room).size();
            boolean joinable = playerCount < MAX_PLAYER_COUNT;
            boolean playable = (room.status() == GameRoomStatus.WAITING || room.status() == GameRoomStatus.FINISHED)
                    && readyCount < READY_TO_START_COUNT;
            return new GameRoomListItem(
                    room.roomId(),
                    room.status(),
                    playerCount,
                    MAX_PLAYER_COUNT,
                    readyCount,
                    joinable,
                    playable,
                    room.updatedAt()
            );
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

        if (room.players().size() >= MAX_PLAYER_COUNT) {
            throw new IllegalArgumentException("房间已满");
        }

        GamePlayer player = new GamePlayer(cleanToken, cleanNickname, now);
        room.players().add(player);
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

    private void handlePlayerExit(GameRoom room, GamePlayer player, Instant now) {
        if (room.status() == GameRoomStatus.PLAYING && isActivePlayer(player) && room.state().winner() == EMPTY) {
            finishRoomByPlayerLeave(room, player, now);
            return;
        }

        room.players().remove(player);
        appendSystemEvent(room, EVENT_PLAYER_LEFT, eventData("nickname", player.nickname()));
    }

    private void finishRoomByPlayerLeave(GameRoom room, GamePlayer player, Instant now) {
        gameRulesService.resign(room.state(), player.side());
        room.status(GameRoomStatus.FINISHED);
        resetReady(room);
        appendSystemEvent(room, EVENT_LEAVE_WIN, eventData(
                "loser", player.side(),
                "winner", room.state().winner()
        ));
        room.players().remove(player);
        room.touch(now);
    }

    private void startRoom(GameRoom room) {
        List<GamePlayer> selectedPlayers = readyPlayers(room).stream()
                .limit(READY_TO_START_COUNT)
                .toList();
        if (selectedPlayers.size() < READY_TO_START_COUNT) return;

        room.players().forEach(player -> player.side(null));
        if (secureRandom.nextBoolean()) {
            selectedPlayers.get(0).side(X);
            selectedPlayers.get(1).side(O);
        } else {
            selectedPlayers.get(0).side(O);
            selectedPlayers.get(1).side(X);
        }

        room.state().reset();
        resetReady(room);
        room.status(GameRoomStatus.PLAYING);
        room.state().started(true);
        room.state().currentPlayer(X);
        room.state().nextBoard(null);
        appendSystemEvent(room, EVENT_GAME_STARTED, eventData("firstPlayer", X));
    }

    private List<GamePlayer> readyPlayers(GameRoom room) {
        return room.players().stream()
                .filter(GamePlayer::ready)
                .sorted(Comparator.comparing(GamePlayer::readyAt))
                .toList();
    }

    private void finishRoomByBoardResult(GameRoom room) {
        room.status(GameRoomStatus.FINISHED);
        resetReady(room);
        if (room.state().winner() == DRAW) {
            appendSystemEvent(room, EVENT_GAME_DRAW, eventData());
            return;
        }

        appendSystemEvent(room, EVENT_LINE_WIN, eventData(
                "winner", room.state().winner(),
                "line", List.copyOf(room.state().bigBoardWinningLine())
        ));
    }

    private void appendMoveMessages(GameRoom room, int playerSide, GameMove move) {
        Optional<GameRuleEvent> directEvent = room.state().lastRuleEvents().stream()
                .filter(event -> event.boardIndex() == move.bigIndex())
                .findFirst();

        Map<String, Object> eventData = eventData(
                "player", playerSide,
                "bigIndex", move.bigIndex(),
                "smallIndex", move.smallIndex()
        );
        directEvent.ifPresent(event -> eventData.put("filledBoardIndex", event.boardIndex()));
        appendNextTurnDataIfUnfinished(eventData, room);
        appendEventMessage(room, "move", playerSide, null, null, EVENT_MOVE_PLAYED, eventData);

        for (GameRuleEvent event : room.state().lastRuleEvents()) {
            appendSettlementMessage(room, event);
        }
    }

    private void appendSettlementMessage(GameRoom room, GameRuleEvent event) {
        if (!gameRulesService.isPlayer(event.owner())) {
            Map<String, Object> eventData = eventData("boardIndex", event.boardIndex());
            appendNextTurnDataIfUnfinished(eventData, room);
            appendSystemEvent(room, EVENT_BOARD_DRAW, eventData);
            return;
        }

        appendSystemEvent(room, EVENT_BOARD_SETTLEMENT, eventData(
                "owner", event.owner(),
                "boardIndex", event.boardIndex(),
                "filledCount", event.filledCount(),
                "nextPlayer", gameRulesService.opponent(event.owner()),
                "chainCount", Math.max(room.state().lastRuleEvents().size() - 1, 0)
        ));
    }

    private void appendNextTurnDataIfUnfinished(Map<String, Object> eventData, GameRoom room) {
        GameCoreState state = room.state();
        if (state.winner() != EMPTY) return;
        eventData.put("nextPlayer", state.currentPlayer());
        eventData.put("nextBoard", state.nextBoard());
    }

    private void appendChatMessage(GameRoom room, GamePlayer player, String text) {
        Integer side = player.side();
        appendEventMessage(room, "chat", null, side, player.nickname(), EVENT_CHAT_SENT, eventData(
                "nickname", player.nickname(),
                "text", text
        ));
    }

    private void appendSystemEvent(GameRoom room, String eventType, Map<String, Object> eventData) {
        appendEventMessage(room, "system", null, null, null, eventType, eventData);
    }

    private void appendEventMessage(
            GameRoom room,
            String type,
            Integer player,
            Integer sender,
            String senderName,
            String eventType,
            Map<String, Object> eventData
    ) {
        room.messages().add(new GameLogMessage(
                "game-" + System.currentTimeMillis() + "-" + room.messages().size(),
                type,
                player,
                sender,
                senderName,
                "",
                eventType,
                eventData,
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
            messages.add(new OutboundMessage(session, GameServerMessage.snapshot(createSnapshot(room, player.side(), player.token()))));
        }
        return messages;
    }

    private GameRoomSnapshot createSnapshot(GameRoom room, Integer selfSide, String selfToken) {
        GameCoreState state = room.state();
        return new GameRoomSnapshot(
                room.roomId(),
                room.status(),
                selfSide,
                room.sortedPlayers().stream()
                        .map(player -> new GamePlayerSnapshot(
                                player.side(),
                                player.nickname(),
                                player.connected(),
                                player.ready(),
                                player.spectator(),
                                player.token().equals(selfToken)))
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

    private Integer requireActiveSide(GamePlayer player, String message) {
        if (!isActivePlayer(player)) {
            throw new IllegalArgumentException(message);
        }
        return player.side();
    }

    private boolean isActivePlayer(GamePlayer player) {
        return player.side() != null && gameRulesService.isPlayer(player.side());
    }

    private void resetReady(GameRoom room) {
        room.players().forEach(player -> {
            player.ready(false);
            player.readyAt(null);
        });
    }

    private void removeRoomIfEmpty(GameRoom room) {
        synchronized (room) {
            if (room.players().isEmpty()) {
                rooms.remove(room.roomId());
            }
        }
    }

    private Map<String, Object> eventData(Object... entries) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            if (!(entries[index] instanceof String key)) {
                throw new IllegalArgumentException("日志事件字段名必须是字符串");
            }
            data.put(key, entries[index + 1]);
        }
        return data;
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
