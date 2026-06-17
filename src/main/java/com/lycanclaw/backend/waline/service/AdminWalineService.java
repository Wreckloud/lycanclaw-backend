package com.lycanclaw.backend.waline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lycanclaw.backend.admin.auth.service.AdminSessionService;
import com.lycanclaw.backend.common.security.AdminAuthPrincipal;
import com.lycanclaw.backend.waline.dto.AdminWalineUserDto;
import com.lycanclaw.backend.waline.dto.AdminWalineUserListDto;
import com.lycanclaw.backend.waline.dto.AdminWalineUserUpdateRequest;
import com.lycanclaw.backend.waline.dto.WalineImportResultDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Waline 管理服务。
 * 代理当前管理员的 Waline 会话，提供用户管理和数据导入导出能力。
 * @author Wreckloud
 * @since 2026-06-17
 */
@Service
public class AdminWalineService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_IMPORT_ROWS = 10000;
    private static final Set<String> IMPORT_TABLES = Set.of("Comment", "Counter", "Users");
    private static final Set<String> USER_TYPES = Set.of("administrator", "guest", "banned");

    private final AdminSessionService adminSessionService;
    private final WalineGatewayClient walineGatewayClient;
    private final ObjectMapper objectMapper;

    public AdminWalineService(
            AdminSessionService adminSessionService,
            WalineGatewayClient walineGatewayClient,
            ObjectMapper objectMapper
    ) {
        this.adminSessionService = adminSessionService;
        this.walineGatewayClient = walineGatewayClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询 Waline 用户分页数据。
     */
    public AdminWalineUserListDto listUsers(String adminToken, int page, int pageSize, String keyword) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize, 50));
        String email = looksLikeEmail(keyword) ? keyword.trim() : "";
        JsonNode payload = walineGatewayClient.fetchAdminUsers(
                requireWalineToken(adminToken),
                safePage,
                safePageSize,
                email
        );

        List<AdminWalineUserDto> users = new ArrayList<>();
        JsonNode items = payload.isArray() ? payload : payload.path("data");
        if (items.isArray()) {
            for (JsonNode item : items) {
                AdminWalineUserDto user = parseUser(item);
                if (matchesKeyword(user, keyword)) {
                    users.add(user);
                }
            }
        }

        return new AdminWalineUserListDto(
                payload.path("page").asInt(safePage),
                Math.max(0, payload.path("totalPages").asInt(0)),
                payload.path("pageSize").asInt(safePageSize),
                totalCount(payload, users.size()),
                users
        );
    }

    /**
     * 更新 Waline 用户资料或用户类型。
     */
    public AdminWalineUserDto updateUser(
            String adminToken,
            String userId,
            AdminWalineUserUpdateRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("更新内容不能为空");
        }
        String normalizedUserId = normalizeUserId(userId);
        AdminAuthPrincipal principal = requireSessionPrincipal(adminToken);
        ObjectNode body = objectMapper.createObjectNode();
        putIfPresent(body, "display_name", request.displayName());
        putIfPresent(body, "email", request.email());
        putIfPresent(body, "url", request.url());
        putIfPresent(body, "avatar", request.avatar());
        putIfPresent(body, "label", request.label());
        if (request.type() != null && !request.type().isBlank()) {
            String type = request.type().trim().toLowerCase();
            if (!USER_TYPES.contains(type)) {
                throw new IllegalArgumentException("用户类型仅支持 administrator、guest 或 banned");
            }
            if (normalizedUserId.equals(principal.userId()) && !"administrator".equals(type)) {
                throw new IllegalArgumentException("不能降级或禁用当前登录管理员");
            }
            body.put("type", type);
        }
        if (body.isEmpty()) {
            throw new IllegalArgumentException("请提供需要修改的用户字段");
        }

        JsonNode updated = walineGatewayClient.updateAdminUser(requireWalineToken(adminToken), normalizedUserId, body);
        return parseUser(updated);
    }

    /**
     * 禁用指定 Waline 用户。
     */
    public void deleteUser(String adminToken, String userId) {
        String normalizedUserId = normalizeUserId(userId);
        AdminAuthPrincipal principal = requireSessionPrincipal(adminToken);
        if (normalizedUserId.equals(principal.userId())) {
            throw new IllegalArgumentException("不能禁用当前登录管理员");
        }
        walineGatewayClient.deleteAdminUser(requireWalineToken(adminToken), normalizedUserId);
    }

    /**
     * 导出 Waline 数据库快照。
     */
    public JsonNode exportDatabase(String adminToken) {
        return walineGatewayClient.exportDatabase(requireWalineToken(adminToken));
    }

    /**
     * 导入 Waline 导出的 JSON 快照。
     */
    public WalineImportResultDto importDatabase(String adminToken, JsonNode payload) {
        if (payload == null || payload.isNull() || !payload.path("data").isObject()) {
            throw new IllegalArgumentException("请上传 Waline 导出的 JSON 文件");
        }
        JsonNode data = payload.path("data");
        int tables = 0;
        int rows = 0;
        List<String> skippedTables = new ArrayList<>();
        String walineToken = requireWalineToken(adminToken);

        for (String table : IMPORT_TABLES) {
            JsonNode tableRows = data.path(table);
            if (!tableRows.isArray()) {
                skippedTables.add(table);
                continue;
            }
            tables++;
            for (JsonNode row : tableRows) {
                if (rows >= MAX_IMPORT_ROWS) {
                    throw new IllegalArgumentException("单次导入最多支持 " + MAX_IMPORT_ROWS + " 条记录");
                }
                if (row != null && row.isObject()) {
                    walineGatewayClient.importDatabaseRow(walineToken, table, row);
                    rows++;
                }
            }
        }
        return new WalineImportResultDto(tables, rows, skippedTables);
    }

    private String requireWalineToken(String adminToken) {
        return adminSessionService.findWalineToken(adminToken)
                .orElseThrow(() -> new IllegalArgumentException(
                        "该操作需要使用 Waline QQ 或 GitHub 登录"
                ));
    }

    private AdminAuthPrincipal requireSessionPrincipal(String adminToken) {
        return adminSessionService.verify(adminToken)
                .orElseThrow(() -> new IllegalArgumentException("当前管理员会话无效"));
    }

    private AdminWalineUserDto parseUser(JsonNode node) {
        return new AdminWalineUserDto(
                text(node, "objectId", "id", "_id"),
                text(node, "display_name", "displayName", "nick", "name"),
                text(node, "email", "mail"),
                text(node, "url", "link"),
                text(node, "avatar", "avatarUrl"),
                text(node, "type"),
                text(node, "label"),
                text(node, "github"),
                text(node, "qq"),
                text(node, "createdAt", "insertedAt"),
                text(node, "updatedAt")
        );
    }

    private boolean matchesKeyword(AdminWalineUserDto user, String keyword) {
        String value = keyword == null ? "" : keyword.trim().toLowerCase();
        if (value.isBlank()) return true;
        return contains(user.displayName(), value)
                || contains(user.email(), value)
                || contains(user.qq(), value)
                || contains(user.github(), value);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword);
    }

    private boolean looksLikeEmail(String keyword) {
        return keyword != null && keyword.contains("@") && keyword.contains(".");
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        return userId.trim();
    }

    private int totalCount(JsonNode payload, int fallback) {
        int total = payload.path("totalCount").asInt(-1);
        if (total >= 0) return total;
        total = payload.path("total").asInt(-1);
        if (total >= 0) return total;
        total = payload.path("count").asInt(-1);
        return Math.max(0, total >= 0 ? total : fallback);
    }

    private void putIfPresent(ObjectNode body, String field, String value) {
        if (value != null && !value.isBlank()) {
            body.put(field, value.trim());
        }
    }

    private String text(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return "";
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }
}
