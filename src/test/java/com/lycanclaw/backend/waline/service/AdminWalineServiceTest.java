package com.lycanclaw.backend.waline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lycanclaw.backend.admin.auth.service.AdminSessionService;
import com.lycanclaw.backend.common.security.AdminAuthPrincipal;
import com.lycanclaw.backend.waline.dto.AdminWalineUserListDto;
import com.lycanclaw.backend.waline.dto.AdminWalineUserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Waline 管理服务测试。
 * 验证用户维护和 Waline 导出的完整业务边界。
 * @author Wreckloud
 * @since 2026-06-24
 */
class AdminWalineServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdminSessionService adminSessionService = mock(AdminSessionService.class);
    private final WalineGatewayClient walineGatewayClient = mock(WalineGatewayClient.class);
    private AdminWalineService service;

    @BeforeEach
    void setUp() {
        when(adminSessionService.findWalineToken("admin")).thenReturn(Optional.of("waline-token"));
        when(adminSessionService.verify("admin")).thenReturn(Optional.of(new AdminAuthPrincipal(
                "session", "user-1", "Wreckloud", "owner@example.com", "123456",
                "administrator", ""
        )));
        service = new AdminWalineService(
                adminSessionService,
                walineGatewayClient,
                objectMapper
        );
    }

    @Test
    void readsSingleUserReturnedByEmailSearch() throws Exception {
        JsonNode user = objectMapper.readTree("""
                {"objectId":"user-1","display_name":"Wreckloud","email":"owner@example.com"}
                """);
        when(walineGatewayClient.fetchAdminUsers("waline-token", 1, 10, "owner@example.com"))
                .thenReturn(user);

        AdminWalineUserListDto result = service.listUsers("admin", 1, 10, "owner@example.com");

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).email()).isEqualTo("owner@example.com");
    }

    @Test
    void writesEmptyLabelWhenRemovingUserLabel() {
        ObjectNode updated = objectMapper.createObjectNode();
        updated.put("objectId", "user-2");
        when(walineGatewayClient.updateAdminUser(eq("waline-token"), eq("user-2"), any(JsonNode.class)))
                .thenReturn(updated);

        service.updateUser("admin", "user-2", new AdminWalineUserUpdateRequest(
                null, null, null, null, null, ""
        ));

        ArgumentCaptor<JsonNode> bodyCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(walineGatewayClient).updateAdminUser(eq("waline-token"), eq("user-2"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().has("label")).isTrue();
        assertThat(bodyCaptor.getValue().path("label").asText()).isEmpty();
    }

    @Test
    void exportsWalineSnapshotPayload() {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("type", "waline");
        snapshot.put("version", 1);
        snapshot.putObject("data");
        ObjectNode response = objectMapper.createObjectNode();
        response.set("data", snapshot);
        when(walineGatewayClient.exportDatabase("waline-token")).thenReturn(response);

        JsonNode result = service.exportDatabase("admin");

        assertThat(result).isSameAs(snapshot);
    }

}
