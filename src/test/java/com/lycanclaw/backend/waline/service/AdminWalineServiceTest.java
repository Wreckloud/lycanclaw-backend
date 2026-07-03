package com.lycanclaw.backend.waline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lycanclaw.backend.admin.auth.service.AdminSessionService;
import com.lycanclaw.backend.common.security.AdminAuthPrincipal;
import com.lycanclaw.backend.stats.service.ArticleMetricSyncService;
import com.lycanclaw.backend.waline.dto.AdminWalineUserListDto;
import com.lycanclaw.backend.waline.dto.AdminWalineUserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Waline 管理服务测试。
 * 验证用户维护和空库初始化导入的完整业务边界。
 * @author Wreckloud
 * @since 2026-06-24
 */
class AdminWalineServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdminSessionService adminSessionService = mock(AdminSessionService.class);
    private final WalineGatewayClient walineGatewayClient = mock(WalineGatewayClient.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ArticleMetricSyncService articleMetricSyncService = mock(ArticleMetricSyncService.class);
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
                objectMapper,
                jdbcTemplate,
                articleMetricSyncService
        );
        ReflectionTestUtils.setField(service, "authorEmail", "owner@example.com");
        ReflectionTestUtils.setField(service, "adminQqWhitelist", "123456,UID_OWNER");
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
    void importsValidatedSnapshotInFixedOrderAndTriggersMetricSync() {
        JsonNode payload = snapshot(false);
        when(walineGatewayClient.exportDatabase("waline-token")).thenReturn(envelope(snapshot(true)));

        var result = service.importDatabase("admin", payload);

        InOrder order = inOrder(walineGatewayClient);
        order.verify(walineGatewayClient).importDatabaseRow(eq("waline-token"), eq("Users"), any(JsonNode.class));
        order.verify(walineGatewayClient).importDatabaseRow(eq("waline-token"), eq("Comment"), any(JsonNode.class));
        order.verify(walineGatewayClient).importDatabaseRow(eq("waline-token"), eq("Counter"), any(JsonNode.class));
        assertThat(result.importedTables()).isEqualTo(3);
        assertThat(result.importedRows()).isEqualTo(3);
        verify(jdbcTemplate).update(any(String.class), eq("owner@example.com"), eq("123456,UID_OWNER"));
        verify(articleMetricSyncService).triggerAsyncSync("waline-import");
    }

    @Test
    void allowsImportWhenOnlyBootstrapAdminUserExists() {
        JsonNode payload = snapshot(false);
        JsonNode current = snapshot(true);
        ((ArrayNode) current.path("data").path("Users")).addObject()
                .put("display_name", "Wreckloud")
                .put("type", "administrator")
                .put("qq", "UID_OWNER");
        when(walineGatewayClient.exportDatabase("waline-token")).thenReturn(envelope(current));

        service.importDatabase("admin", payload);

        InOrder order = inOrder(walineGatewayClient);
        order.verify(walineGatewayClient).clearDatabaseTable("waline-token", "Users");
        order.verify(walineGatewayClient).importDatabaseRow(eq("waline-token"), eq("Users"), any(JsonNode.class));
        verify(jdbcTemplate).execute("ALTER TABLE `wl_Users` AUTO_INCREMENT = 1");
        verify(jdbcTemplate).update(any(String.class), eq("owner@example.com"), eq("123456,UID_OWNER"));
    }

    @Test
    void rejectsImportWhenCurrentDatabaseIsNotEmpty() {
        JsonNode current = snapshot(true);
        ((ArrayNode) current.path("data").path("Comment")).addObject().put("objectId", "existing");
        when(walineGatewayClient.exportDatabase("waline-token")).thenReturn(envelope(current));

        assertThatThrownBy(() -> service.importDatabase("admin", snapshot(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数据库非空");

        verify(walineGatewayClient, never()).importDatabaseRow(any(), any(), any());
    }

    @Test
    void rejectsImportWhenCurrentDatabaseAlreadyHasRealUsers() {
        JsonNode current = snapshot(true);
        ((ArrayNode) current.path("data").path("Users")).addObject()
                .put("email", "visitor@example.com")
                .put("display_name", "访客")
                .put("type", "guest");
        when(walineGatewayClient.exportDatabase("waline-token")).thenReturn(envelope(current));

        assertThatThrownBy(() -> service.importDatabase("admin", snapshot(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已有用户数据");

        verify(walineGatewayClient, never()).importDatabaseRow(any(), any(), any());
    }

    @Test
    void cleansAllTablesWhenImportFails() {
        when(walineGatewayClient.exportDatabase("waline-token")).thenReturn(envelope(snapshot(true)));
        doThrow(new IllegalStateException("upstream failed"))
                .when(walineGatewayClient)
                .importDatabaseRow(eq("waline-token"), eq("Comment"), any(JsonNode.class));

        assertThatThrownBy(() -> service.importDatabase("admin", snapshot(false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已清理本次写入");

        verify(walineGatewayClient).clearDatabaseTable("waline-token", "Comment");
        verify(walineGatewayClient).clearDatabaseTable("waline-token", "Counter");
        verify(walineGatewayClient).clearDatabaseTable("waline-token", "Users");
        verify(jdbcTemplate).execute("ALTER TABLE `wl_Comment` AUTO_INCREMENT = 1");
        verify(jdbcTemplate).execute("ALTER TABLE `wl_Counter` AUTO_INCREMENT = 1");
        verify(jdbcTemplate).execute("ALTER TABLE `wl_Users` AUTO_INCREMENT = 1");
    }

    @Test
    void rejectsMalformedSnapshotBeforeReadingCurrentDatabase() {
        ObjectNode malformed = objectMapper.createObjectNode();
        malformed.put("type", "waline");
        malformed.put("version", 1);

        assertThatThrownBy(() -> service.importDatabase("admin", malformed))
                .isInstanceOf(IllegalArgumentException.class);

        verify(walineGatewayClient, never()).exportDatabase(any());
    }

    private ObjectNode snapshot(boolean empty) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("type", "waline");
        snapshot.put("version", 1);
        snapshot.putArray("tables").add("Comment").add("Counter").add("Users");
        ObjectNode data = snapshot.putObject("data");
        ArrayNode users = data.putArray("Users");
        ArrayNode comments = data.putArray("Comment");
        ArrayNode counters = data.putArray("Counter");
        if (!empty) {
            users.addObject().put("display_name", "Wreckloud");
            comments.addObject().put("nick", "访客").put("comment", "测试");
            counters.addObject().put("url", "/thoughts/test.html").put("time", 1);
        }
        return snapshot;
    }

    private ObjectNode envelope(JsonNode snapshot) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("errno", 0);
        response.set("data", snapshot);
        return response;
    }
}
