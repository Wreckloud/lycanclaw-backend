package com.lycanclaw.backend.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.game.dto.CreateGameRoomResponse;
import com.lycanclaw.backend.game.dto.GameRoomSnapshot;
import com.lycanclaw.backend.game.model.GameRoomStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;

import static com.lycanclaw.backend.game.model.GameConstants.O;
import static com.lycanclaw.backend.game.model.GameConstants.X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 在线对战房间服务测试。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
class GameRoomServiceTest {

    private final GameRoomService service = new GameRoomService(new GameRulesService(), new ObjectMapper());

    @Test
    void createsRoomAndRejectsInvalidNickname() {
        CreateGameRoomResponse room = service.createRoom("维克罗德");

        assertThat(room.roomId()).isNotBlank();
        assertThat(room.playerToken()).isNotBlank();
        assertThatThrownBy(() -> service.createRoom("abcdefghijklmnopq"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("昵称不能超过");
    }

    @Test
    void allowsFourMembersAndRejectsFifthMember() {
        RoomFixture room = waitingRoomWithMembers(4);
        GameRoomSnapshot snapshot = service.snapshotForPlayer(room.roomId(), room.token(0)).orElseThrow();

        assertThat(snapshot.roomStatus()).isEqualTo(GameRoomStatus.WAITING);
        assertThat(snapshot.players()).hasSize(4);
        assertThat(service.listRooms()).singleElement()
                .satisfies(item -> {
                    assertThat(item.playerCount()).isEqualTo(4);
                    assertThat(item.joinable()).isFalse();
                });
        assertThatThrownBy(() -> service.join(session("fifth"), room.roomId(), "token-5", "五号"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("房间已满");
    }

    @Test
    void firstTwoReadyMembersStartGameAndOthersSpectate() {
        RoomFixture room = waitingRoomWithMembers(4);

        service.markReady(room.roomId(), room.token(0));
        GameRoomSnapshot waiting = service.snapshotForPlayer(room.roomId(), room.token(0)).orElseThrow();
        assertThat(waiting.roomStatus()).isEqualTo(GameRoomStatus.WAITING);
        assertThat(waiting.players()).filteredOn("ready", true).hasSize(1);

        service.markReady(room.roomId(), room.token(1));
        GameRoomSnapshot started = service.snapshotForPlayer(room.roomId(), room.token(0)).orElseThrow();

        assertThat(started.roomStatus()).isEqualTo(GameRoomStatus.PLAYING);
        assertThat(started.state().isStarted()).isTrue();
        assertThat(started.players()).filteredOn("side", X).hasSize(1);
        assertThat(started.players()).filteredOn("side", O).hasSize(1);
        assertThat(started.players()).filteredOn("spectator", true).hasSize(2);
        assertThat(started.messages()).anySatisfy(message -> {
            assertThat(message.eventType()).isEqualTo("GAME_STARTED");
            assertThat(message.eventData()).containsEntry("firstPlayer", X);
            assertThat(message.text()).isEmpty();
        });
        assertThatThrownBy(() -> service.playMove(room.roomId(), room.token(2), 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("观战者不能落子");
        assertThatThrownBy(() -> service.resign(room.roomId(), room.token(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("观战者不能投降");
    }

    @Test
    void readyCanBeCancelledBeforeGameStarts() {
        RoomFixture room = waitingRoomWithMembers(2);

        service.markReady(room.roomId(), room.token(0));
        service.markReady(room.roomId(), room.token(0));
        GameRoomSnapshot snapshot = service.snapshotForPlayer(room.roomId(), room.token(0)).orElseThrow();

        assertThat(snapshot.roomStatus()).isEqualTo(GameRoomStatus.WAITING);
        assertThat(snapshot.players()).filteredOn("ready", true).isEmpty();
        assertThat(snapshot.messages()).anySatisfy(message -> assertThat(message.eventType()).isEqualTo("PLAYER_UNREADY"));
    }

    @Test
    void rejectsNonCurrentPlayerMoveAndAcceptsCurrentPlayerMove() {
        RoomFixture room = startedRoom();
        String xToken = tokenForSide(room, X);
        String oToken = tokenForSide(room, O);

        assertThatThrownBy(() -> service.playMove(room.roomId(), oToken, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("还没轮到你");

        service.playMove(room.roomId(), xToken, 0, 0);
        GameRoomSnapshot snapshot = service.snapshotForPlayer(room.roomId(), xToken).orElseThrow();

        assertThat(snapshot.state().board()[0]).isEqualTo(X);
        assertThat(snapshot.state().currentPlayer()).isEqualTo(O);
        assertThat(snapshot.state().nextBoard()).isEqualTo(0);
    }

    @Test
    void limitsChatAndAllowsChatAfterFinish() {
        RoomFixture room = startedRoom();
        String xToken = tokenForSide(room, X);

        assertThatThrownBy(() -> service.sendChat(room.roomId(), xToken, "x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("聊天内容不能超过");

        service.sendChat(room.roomId(), xToken, "你好");
        service.resign(room.roomId(), xToken);
        service.sendChat(room.roomId(), xToken, "还能聊");
        GameRoomSnapshot snapshot = service.snapshotForPlayer(room.roomId(), xToken).orElseThrow();

        assertThat(snapshot.roomStatus()).isEqualTo(GameRoomStatus.FINISHED);
        assertThat(snapshot.state().winner()).isEqualTo(O);
        assertThat(snapshot.messages()).anySatisfy(message -> {
            assertThat(message.eventType()).isEqualTo("CHAT_SENT");
            assertThat(message.eventData()).containsEntry("text", "还能聊");
        });
    }

    @Test
    void finishedRoomCanStartAgainWhenAnyTwoMembersReady() {
        RoomFixture room = startedRoom();
        String xToken = tokenForSide(room, X);
        service.resign(room.roomId(), xToken);

        service.markReady(room.roomId(), room.token(2));
        service.markReady(room.roomId(), room.token(3));
        GameRoomSnapshot snapshot = service.snapshotForPlayer(room.roomId(), room.token(2)).orElseThrow();

        assertThat(snapshot.roomStatus()).isEqualTo(GameRoomStatus.PLAYING);
        assertThat(snapshot.state().winner()).isZero();
        assertThat(snapshot.state().moveHistory()).isEmpty();
        assertThat(snapshot.state().isStarted()).isTrue();
        assertThat(snapshot.players()).filteredOn("spectator", false).hasSize(2);
    }

    @Test
    void activePlayerDisconnectsAndLosesByForfeit() {
        RoomFixture room = startedRoom();
        int xIndex = indexForSide(room, X);
        String oToken = tokenForSide(room, O);

        service.disconnect(room.session(xIndex));
        GameRoomSnapshot finishedSnapshot = service.snapshotForPlayer(room.roomId(), oToken).orElseThrow();

        assertThat(finishedSnapshot.roomStatus()).isEqualTo(GameRoomStatus.FINISHED);
        assertThat(finishedSnapshot.state().winner()).isEqualTo(O);
        assertThat(finishedSnapshot.messages()).anySatisfy(message -> assertThat(message.eventType()).isEqualTo("LEAVE_WIN"));
    }

    private RoomFixture startedRoom() {
        RoomFixture room = waitingRoomWithMembers(4);
        service.markReady(room.roomId(), room.token(0));
        service.markReady(room.roomId(), room.token(1));
        return room;
    }

    private RoomFixture waitingRoomWithMembers(int count) {
        CreateGameRoomResponse room = service.createRoom("一号");
        String[] tokens = {room.playerToken(), "token-2", "token-3", "token-4"};
        String[] names = {"一号", "二号", "三号", "四号"};
        WebSocketSession[] sessions = new WebSocketSession[tokens.length];
        for (int index = 0; index < count; index++) {
            sessions[index] = session("session-" + index);
            service.join(sessions[index], room.roomId(), tokens[index], names[index]);
        }
        return new RoomFixture(room.roomId(), tokens, sessions);
    }

    private String tokenForSide(RoomFixture room, int side) {
        return room.token(indexForSide(room, side));
    }

    private int indexForSide(RoomFixture room, int side) {
        for (String token : room.tokens()) {
            GameRoomSnapshot snapshot = service.snapshotForPlayer(room.roomId(), token).orElseThrow();
            if (snapshot.selfSide() != null && snapshot.selfSide() == side) {
                for (int index = 0; index < room.tokens().length; index++) {
                    if (room.token(index).equals(token)) {
                        return index;
                    }
                }
            }
        }
        throw new AssertionError("missing side " + side);
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        try {
            doNothing().when(session).sendMessage(any());
        } catch (Exception ignored) {
            throw new IllegalStateException("mock setup failed");
        }
        return session;
    }

    private record RoomFixture(String roomId, String[] tokens, WebSocketSession[] sessions) {

        String token(int index) {
            return tokens[index];
        }

        WebSocketSession session(int index) {
            return sessions[index];
        }
    }
}
