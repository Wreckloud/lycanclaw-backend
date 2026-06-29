package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.EncouragementSettleRequest;
import com.lycanclaw.backend.analytics.entity.EncouragementEventEntity;
import com.lycanclaw.backend.analytics.repository.EncouragementEventRepository;
import com.lycanclaw.backend.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 首页催更结算服务测试。
 * 验证合法批次入库，以及增量和访客标识的明确边界。
 *
 * @author Wreckloud
 * @since 2026-06-24
 */
class EncouragementServiceTest {

    @Test
    void savesValidatedSettlementBatch() {
        EncouragementEventRepository repository = mock(EncouragementEventRepository.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(clientIpResolver.resolve(servletRequest)).thenReturn("203.0.113.10");
        when(servletRequest.getHeader("User-Agent")).thenReturn("Test Browser");
        when(repository.save(any(EncouragementEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        EncouragementService service = createService(repository, clientIpResolver);

        int savedDelta = service.settle(
                new EncouragementSettleRequest(12, "visitor-1"),
                servletRequest
        ).delta();

        ArgumentCaptor<EncouragementEventEntity> captor =
                ArgumentCaptor.forClass(EncouragementEventEntity.class);
        verify(repository).save(captor.capture());
        EncouragementEventEntity saved = captor.getValue();
        assertThat(savedDelta).isEqualTo(12);
        assertThat(saved.getVisitorId()).isEqualTo("visitor-1");
        assertThat(saved.getIp()).isEqualTo("203.0.113.10");
        assertThat(saved.getUserAgent()).isEqualTo("Test Browser");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsInvalidSettlementDelta() {
        EncouragementService service = createService(
                mock(EncouragementEventRepository.class),
                mock(ClientIpResolver.class)
        );
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThatThrownBy(() -> service.settle(new EncouragementSettleRequest(0, "visitor-1"), request))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.settle(new EncouragementSettleRequest(501, "visitor-1"), request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingOrOversizedVisitorId() {
        EncouragementService service = createService(
                mock(EncouragementEventRepository.class),
                mock(ClientIpResolver.class)
        );
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThatThrownBy(() -> service.settle(new EncouragementSettleRequest(1, ""), request))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.settle(new EncouragementSettleRequest(1, "v".repeat(97)), request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EncouragementService createService(
            EncouragementEventRepository repository,
            ClientIpResolver clientIpResolver
    ) {
        return new EncouragementService(repository, clientIpResolver, "Asia/Shanghai");
    }
}
