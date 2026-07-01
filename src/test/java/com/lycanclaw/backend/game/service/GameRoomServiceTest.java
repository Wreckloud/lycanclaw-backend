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
    void joiningSecondPlayerStartsGameAndRejectsThirdPlayer() {
        CreateGameRoomResponse room = service.createRoom("蓝方");
        service.join(session("x"), room.roomId(), room.playerToken(), "蓝方");
        String redToken = "red-token";

        service.join(session("o"), room.roomId(), redToken, "红方");
        GameRoomSnapshot snapshot = service.snapshotForPlayer(room.roomId(), room.playerToken()).orElseThrow();

        assertThat(snapshot.roomStatus()).isEqualTo(GameRoomStatus.PLAYING);
        assertThat(snapshot.state().isStarted()).isTrue();
        assertThat(snapshot.state().currentPlayer()).isEqualTo(X);
        assertThat(snapshot.players()).hasSize(2);
        assertThatThrownBy(() -> service.join(session("third"), room.roomId(), "third-token", "第三人"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("房间已满");
    }

    @Test
    void rejectsNonCurrentPlayerMoveAndAcceptsCurrentPlayerMove() {
        CreateGameRoomResponse room = startedRoom();
        String redToken = "red-token";

        assertThatThrownBy(() -> service.playMove(room.roomId(), redToken, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("还没轮到你");

        service.playMove(room.roomId(), room.playerToken(), 0, 0);
        GameRoomSnapshot snapshot = service.snapshotForPlayer(room.roomId(), room.playerToken()).orElseThrow();

        assertThat(snapshot.state().board()[0]).isEqualTo(X);
        assertThat(snapshot.state().currentPlayer()).isEqualTo(O);
        assertThat(snapshot.state().nextBoard()).isEqualTo(0);
    }

    @Test
    void limitsChatAndFinishesByResign() {
        CreateGameRoomResponse room = startedRoom();

        assertThatThrownBy(() -> service.sendChat(room.roomId(), room.playerToken(), "x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("聊天内容不能超过");

        service.sendChat(room.roomId(), room.playerToken(), "你好");
        service.resign(room.roomId(), room.playerToken());
        GameRoomSnapshot snapshot = service.snapshotForPlayer(room.roomId(), room.playerToken()).orElseThrow();

        assertThat(snapshot.roomStatus()).isEqualTo(GameRoomStatus.FINISHED);
        assertThat(snapshot.state().winner()).isEqualTo(O);
        assertThatThrownBy(() -> service.sendChat(room.roomId(), room.playerToken(), "还能聊吗"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能继续聊天");
    }

    private CreateGameRoomResponse startedRoom() {
        CreateGameRoomResponse room = service.createRoom("蓝方");
        service.join(session("x"), room.roomId(), room.playerToken(), "蓝方");
        service.join(session("o"), room.roomId(), "red-token", "红方");
        return room;
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
}
