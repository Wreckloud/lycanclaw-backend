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
import com.lycanclaw.backend.stats.service.ArticleMetricSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Waline 管理服务。
 * 代理当前管理员的 Waline 会话，提供用户管理和数据导入导出能力。
 * @author Wreckloud
 * @since 2026-06-17
 */
@Service
public class AdminWalineService {

    private static final Logger log = LoggerFactory.getLogger(AdminWalineService.class);
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_IMPORT_ROWS = 10000;
    private static final List<String> IMPORT_TABLES = List.of("Users", "Comment", "Counter");
    private static final List<String> CLEANUP_TABLES = List.of("Comment", "Counter", "Users");
    private static final Map<String, String> PHYSICAL_TABLES = Map.of(
            "Users", "wl_Users",
            "Comment", "wl_Comment",
            "Counter", "wl_Counter"
    );
    private static final Set<String> USER_TYPES = Set.of("administrator", "guest", "banned");

    private final AdminSessionService adminSessionService;
    private final WalineGatewayClient walineGatewayClient;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ArticleMetricSyncService articleMetricSyncService;

    public AdminWalineService(
            AdminSessionService adminSessionService,
            WalineGatewayClient walineGatewayClient,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            ArticleMetricSyncService articleMetricSyncService
    ) {
        this.adminSessionService = adminSessionService;
        this.walineGatewayClient = walineGatewayClient;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.articleMetricSyncService = articleMetricSyncService;
    }

    /**
     * 查询 Waline 用户分页数据。
     */
    public AdminWalineUserListDto listUsers(String adminToken, int page, int pageSize, String keyword) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize, 50));
        String email = normalizeEmailKeyword(keyword);
        JsonNode payload = walineGatewayClient.fetchAdminUsers(
                requireWalineToken(adminToken),
                safePage,
                safePageSize,
                email
        );

        List<AdminWalineUserDto> users = new ArrayList<>();
        JsonNode items = payload.isArray() ? payload : payload.path("data");
        if (items.isMissingNode() && payload.isObject() && payload.has("objectId")) {
            items = payload;
        }
        if (items.isArray()) {
            for (JsonNode item : items) {
                users.add(parseUser(item));
            }
        } else if (items.isObject()) {
            users.add(parseUser(items));
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
        putIfNotNull(body, "label", request.label());
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
        return walineSnapshot(walineGatewayClient.exportDatabase(requireWalineToken(adminToken)));
    }

    /**
     * 向空数据库初始化导入 Waline JSON 快照。
     */
    public WalineImportResultDto importDatabase(String adminToken, JsonNode payload) {
        ValidatedImport validated = validateImport(payload);
        String walineToken = requireWalineToken(adminToken);
        ensureWalineDatabaseEmpty(walineGatewayClient.exportDatabase(walineToken));

        try {
            for (String table : IMPORT_TABLES) {
                for (JsonNode row : validated.data().path(table)) {
                    walineGatewayClient.importDatabaseRow(walineToken, table, row);
                }
            }
        } catch (RuntimeException importException) {
            try {
                cleanupFailedImport(walineToken);
            } catch (RuntimeException cleanupException) {
                importException.addSuppressed(cleanupException);
                throw new IllegalStateException("Waline 初始化导入失败，且自动清理未完成，请先检查数据库", importException);
            }
            throw new IllegalStateException("Waline 初始化导入失败，已清理本次写入，可重新导入", importException);
        }

        try {
            articleMetricSyncService.triggerAsyncSync("waline-import");
        } catch (RuntimeException ex) {
            log.warn("Waline 导入成功，但文章指标同步任务提交失败", ex);
        }
        return new WalineImportResultDto(IMPORT_TABLES.size(), validated.rows(), List.of());
    }

    private ValidatedImport validateImport(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("请上传 Waline 导出的 JSON 文件");
        }
        if (!"waline".equals(payload.path("type").asText()) || payload.path("version").asInt(-1) != 1) {
            throw new IllegalArgumentException("仅支持 Waline version 1 导出文件");
        }
        JsonNode tables = payload.path("tables");
        JsonNode data = payload.path("data");
        if (!tables.isArray() || !data.isObject()) {
            throw new IllegalArgumentException("Waline 导出文件缺少表结构信息");
        }

        int rows = 0;
        for (String table : IMPORT_TABLES) {
            if (!containsText(tables, table) || !data.path(table).isArray()) {
                throw new IllegalArgumentException("Waline 导出文件缺少 " + table + " 表");
            }
            for (JsonNode row : data.path(table)) {
                if (row == null || !row.isObject()) {
                    throw new IllegalArgumentException(table + " 表包含无效记录");
                }
                rows++;
                if (rows > MAX_IMPORT_ROWS) {
                    throw new IllegalArgumentException("单次导入最多支持 " + MAX_IMPORT_ROWS + " 条记录");
                }
            }
        }
        return new ValidatedImport(data, rows);
    }

    private void ensureWalineDatabaseEmpty(JsonNode response) {
        JsonNode snapshot = walineSnapshot(response);
        JsonNode data = snapshot.path("data");
        for (String table : IMPORT_TABLES) {
            JsonNode rows = data.path(table);
            if (!rows.isArray()) {
                throw new IllegalStateException("Waline 导出结果缺少 " + table + " 表");
            }
            if (!rows.isEmpty()) {
                throw new IllegalArgumentException("当前 Waline 数据库非空，仅允许初始化空库");
            }
        }
    }

    private JsonNode walineSnapshot(JsonNode response) {
        JsonNode snapshot = response == null ? null : response.path("data");
        if (snapshot == null
                || !"waline".equals(snapshot.path("type").asText())
                || snapshot.path("version").asInt(-1) != 1
                || !snapshot.path("data").isObject()) {
            throw new IllegalStateException("无法读取 Waline 数据库快照");
        }
        return snapshot;
    }

    private void cleanupFailedImport(String walineToken) {
        for (String table : CLEANUP_TABLES) {
            walineGatewayClient.clearDatabaseTable(walineToken, table);
        }
        for (String table : CLEANUP_TABLES) {
            jdbcTemplate.execute("ALTER TABLE `" + PHYSICAL_TABLES.get(table) + "` AUTO_INCREMENT = 1");
        }
    }

    private boolean containsText(JsonNode array, String expected) {
        for (JsonNode item : array) {
            if (expected.equals(item.asText())) {
                return true;
            }
        }
        return false;
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

    private String normalizeEmailKeyword(String keyword) {
        String email = keyword == null ? "" : keyword.trim();
        if (email.isBlank()) {
            return "";
        }
        if (!email.contains("@") || !email.substring(email.indexOf('@') + 1).contains(".")) {
            throw new IllegalArgumentException("用户搜索请输入完整邮箱");
        }
        return email;
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

    private void putIfNotNull(ObjectNode body, String field, String value) {
        if (value != null) {
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

    private record ValidatedImport(JsonNode data, int rows) {
    }
}
