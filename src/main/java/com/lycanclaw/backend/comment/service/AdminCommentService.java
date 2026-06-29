package com.lycanclaw.backend.comment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lycanclaw.backend.admin.auth.service.AdminSessionService;
import com.lycanclaw.backend.common.security.AdminAuthPrincipal;
import com.lycanclaw.backend.comment.dto.AdminCommentItemDto;
import com.lycanclaw.backend.comment.dto.AdminCommentListDto;
import com.lycanclaw.backend.comment.dto.AdminCommentBatchRequest;
import com.lycanclaw.backend.comment.dto.AdminCommentReplyRequest;
import com.lycanclaw.backend.comment.dto.AdminCommentUpdateRequest;
import com.lycanclaw.backend.analytics.service.AnalyticsPathPolicy;
import com.lycanclaw.backend.analytics.service.IpRegionService;
import com.lycanclaw.backend.content.service.ContentCatalogService;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端评论服务。
 * 使用当前 Waline 管理会话读取和维护评论，不向浏览器暴露 Waline token。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Service
public class AdminCommentService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("all", "approved", "waiting", "spam");

    private final AdminSessionService adminSessionService;
    private final WalineGatewayClient walineGatewayClient;
    private final ObjectMapper objectMapper;
    private final CommentTextNormalizer commentTextNormalizer;
    private final ContentCatalogService contentCatalogService;
    private final AnalyticsPathPolicy pathPolicy;
    private final IpRegionService ipRegionService;

    public AdminCommentService(
            AdminSessionService adminSessionService,
            WalineGatewayClient walineGatewayClient,
            ObjectMapper objectMapper,
            CommentTextNormalizer commentTextNormalizer,
            ContentCatalogService contentCatalogService,
            AnalyticsPathPolicy pathPolicy,
            IpRegionService ipRegionService
    ) {
        this.adminSessionService = adminSessionService;
        this.walineGatewayClient = walineGatewayClient;
        this.objectMapper = objectMapper;
        this.commentTextNormalizer = commentTextNormalizer;
        this.contentCatalogService = contentCatalogService;
        this.pathPolicy = pathPolicy;
        this.ipRegionService = ipRegionService;
    }

    /**
     * 查询指定审核状态下的 Waline 评论。
     */
    public AdminCommentListDto list(String adminToken, String status, String keyword, int page) {
        String normalizedStatus = normalizeStatus(status);
        int safePage = Math.max(1, page);
        JsonNode payload = walineGatewayClient.fetchAdminComments(
                requireWalineToken(adminToken),
                safePage,
                normalizedStatus,
                keyword
        );

        JsonNode items = payload.isArray() ? payload : payload.path("data");
        List<AdminCommentItemDto> comments = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                comments.add(parseComment(item));
            }
        }

        int approvedCount = walineGatewayClient.fetchApprovedCommentCount();
        int waitingCount = Math.max(0, payload.path("waitingCount").asInt(0));
        int spamCount = Math.max(0, payload.path("spamCount").asInt(0));
        return new AdminCommentListDto(
                payload.path("page").asInt(safePage),
                Math.max(0, payload.path("totalPages").asInt(0)),
                approvedCount + waitingCount + spamCount,
                approvedCount,
                waitingCount,
                spamCount,
                comments
        );
    }

    /**
     * 更新评论审核状态或置顶状态。
     */
    public AdminCommentItemDto update(
            String adminToken,
            String commentId,
            AdminCommentUpdateRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("更新内容不能为空");
        }

        ObjectNode body = objectMapper.createObjectNode();
        if (request.status() != null && !request.status().isBlank()) {
            body.put("status", normalizeUpdateStatus(request.status()));
        }
        if (request.comment() != null && !request.comment().isBlank()) {
            body.put("comment", request.comment().trim());
        }
        if (request.sticky() != null) {
            body.put("sticky", request.sticky() ? 1 : 0);
        }
        if (body.isEmpty()) {
            throw new IllegalArgumentException("请提供评论正文、审核状态或置顶状态");
        }

        JsonNode updated = walineGatewayClient.updateAdminComment(
                requireWalineToken(adminToken),
                commentId,
                body
        );
        return parseComment(updated);
    }

    /**
     * 删除指定 Waline 评论。
     */
    public void delete(String adminToken, String commentId) {
        walineGatewayClient.deleteAdminComment(requireWalineToken(adminToken), commentId);
    }

    /**
     * 使用当前管理员身份回复指定评论。
     */
    public AdminCommentItemDto reply(
            String adminToken,
            String commentId,
            AdminCommentReplyRequest request
    ) {
        if (request == null || request.comment() == null || request.comment().isBlank()) {
            throw new IllegalArgumentException("回复内容不能为空");
        }
        String url = pathPolicy.normalizePath(request.url());
        AdminAuthPrincipal principal = requireSessionPrincipal(adminToken);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("comment", request.comment().trim());
        body.put("url", url);
        body.put("pid", commentId);
        body.put("rid", request.rootId() == null || request.rootId().isBlank() ? commentId : request.rootId().trim());
        body.put("nick", principal.nickname());
        body.put("mail", principal.email());
        return parseComment(walineGatewayClient.createAdminComment(requireWalineToken(adminToken), body));
    }

    /**
     * 批量执行评论审核或删除操作。
     */
    public Map<String, Object> batch(String adminToken, AdminCommentBatchRequest request) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            throw new IllegalArgumentException("请选择需要操作的评论");
        }
        String action = request.action() == null ? "" : request.action().trim().toLowerCase();
        if (!Set.of("approved", "waiting", "spam", "delete").contains(action)) {
            throw new IllegalArgumentException("批量操作仅支持 approved、waiting、spam 或 delete");
        }
        String walineToken = requireWalineToken(adminToken);
        int handled = 0;
        for (String id : request.ids().stream().filter(value -> value != null && !value.isBlank()).distinct().limit(100).toList()) {
            if ("delete".equals(action)) {
                walineGatewayClient.deleteAdminComment(walineToken, id.trim());
            } else {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("status", action);
                walineGatewayClient.updateAdminComment(walineToken, id.trim(), body);
            }
            handled++;
        }
        return Map.of("handled", handled, "action", action);
    }

    private String requireWalineToken(String adminToken) {
        return adminSessionService.findWalineToken(adminToken)
                .orElseThrow(() -> new IllegalArgumentException(
                        "评论管理需要使用 Waline QQ 或 GitHub 登录"
                ));
    }

    private AdminAuthPrincipal requireSessionPrincipal(String adminToken) {
        return adminSessionService.verify(adminToken)
                .orElseThrow(() -> new IllegalArgumentException("当前管理员会话无效"));
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "all" : status.trim().toLowerCase();
        if (!ALLOWED_STATUSES.contains(value)) {
            throw new IllegalArgumentException("评论状态仅支持 all、approved、waiting 或 spam");
        }
        return value;
    }

    private String normalizeUpdateStatus(String status) {
        String value = status == null ? "" : status.trim().toLowerCase();
        if (!Set.of("approved", "waiting", "spam").contains(value)) {
            throw new IllegalArgumentException("评论审核状态仅支持 approved、waiting 或 spam");
        }
        return value;
    }

    private AdminCommentItemDto parseComment(JsonNode item) {
        String url = pathPolicy.normalizePath(text(item, "url", "path"));
        ContentCatalogService.ContentItem post = contentCatalogService.loadArticleMap().get(url);
        String original = text(item, "orig", "comment", "content");
        return new AdminCommentItemDto(
                text(item, "objectId", "id"),
                text(item, "nick", "name"),
                text(item, "mail", "email"),
                text(item, "link"),
                text(item, "avatar", "avatarUrl"),
                commentTextNormalizer.toPlainText(original),
                original,
                url,
                post == null ? fallbackTitle(url) : post.title(),
                commentTime(item),
                firstNonBlank(text(item, "status"), "approved"),
                item.path("sticky").asBoolean(false) || item.path("sticky").asInt(0) > 0,
                text(item, "rid"),
                text(item, "browser"),
                text(item, "os"),
                text(item, "ip"),
                firstNonBlank(text(item, "addr"), ipRegionService.resolve(text(item, "ip"))),
                text(item, "user_id", "userId"),
                text(item, "type")
        );
    }

    private String commentTime(JsonNode item) {
        String timestamp = text(item, "insertedAt", "createdAt");
        if (!timestamp.isBlank()) {
            return timestamp;
        }
        long epochMillis = item.path("time").asLong(0);
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis).toString() : "";
    }

    private String fallbackTitle(String url) {
        int slash = url.lastIndexOf('/');
        String filename = slash >= 0 ? url.substring(slash + 1) : url;
        return filename.endsWith(".html") ? filename.substring(0, filename.length() - 5) : filename;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
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
