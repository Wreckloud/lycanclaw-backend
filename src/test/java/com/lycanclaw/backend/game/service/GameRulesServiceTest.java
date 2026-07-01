package com.lycanclaw.backend.game.service;

import com.lycanclaw.backend.game.model.GameCoreState;
import com.lycanclaw.backend.game.model.GameMove;
import org.junit.jupiter.api.Test;

import static com.lycanclaw.backend.game.model.GameConstants.EMPTY;
import static com.lycanclaw.backend.game.model.GameConstants.O;
import static com.lycanclaw.backend.game.model.GameConstants.X;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 九宫叠阵在线规则测试。
 *
 * @author Wreckloud
 * @since 2026-07-01
 */
class GameRulesServiceTest {

    private final GameRulesService service = new GameRulesService();

    @Test
    void centerMoveGivesOpponentFreeMove() {
        GameCoreState state = startedState();

        assertThat(service.applyMove(state, new GameMove(0, 4))).isTrue();

        assertThat(state.currentPlayer()).isEqualTo(O);
        assertThat(state.nextBoard()).isNull();
    }

    @Test
    void claimsSmallBoardWhenLineIsCompleted() {
        GameCoreState state = startedState();
        state.nextBoard(0);
        state.board()[0] = X;
        state.board()[1] = X;

        assertThat(service.applyMove(state, new GameMove(0, 2))).isTrue();

        assertThat(state.smallBoardStatus()[0]).isEqualTo(X);
        assertThat(state.smallBoardWinningLines().get(0)).containsExactly(0, 1, 2);
    }

    @Test
    void settledControlledFullBoardFillsEntranceAndResetsTurn() {
        GameCoreState state = startedState();
        state.currentPlayer(X);
        state.nextBoard(3);
        state.smallBoardStatus()[3] = X;
        int offset = 3 * 9;
        int[] cells = {X, X, X, O, O, X, X, O, EMPTY};
        System.arraycopy(cells, 0, state.board(), offset, cells.length);

        assertThat(service.applyMove(state, new GameMove(3, 8))).isTrue();

        assertThat(state.smallBoardResolved()[3]).isTrue();
        assertThat(state.lastRuleEvents()).hasSize(1);
        assertThat(state.lastRuleEvents().get(0).owner()).isEqualTo(X);
        assertThat(state.lastRuleEvents().get(0).filledCount()).isGreaterThan(0);
        assertThat(state.currentPlayer()).isEqualTo(O);
        assertThat(state.nextBoard()).isNull();
    }

    private GameCoreState startedState() {
        GameCoreState state = new GameCoreState();
        state.started(true);
        state.currentPlayer(X);
        return state;
    }
}
