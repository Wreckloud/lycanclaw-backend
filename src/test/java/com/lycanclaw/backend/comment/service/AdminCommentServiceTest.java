package com.lycanclaw.backend.comment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.admin.auth.service.AdminSessionService;
import com.lycanclaw.backend.analytics.service.AnalyticsContentIndexService;
import com.lycanclaw.backend.analytics.service.AnalyticsPathPolicy;
import com.lycanclaw.backend.analytics.service.IpRegionService;
import com.lycanclaw.backend.comment.dto.AdminCommentItemDto;
import com.lycanclaw.backend.comment.dto.AdminCommentListDto;
import com.lycanclaw.backend.comment.dto.AdminCommentUpdateRequest;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端评论服务测试。
 * 验证 Waline 网关结果、原文清理和管理字段能够稳定转换。
 * @author Wreckloud
 * @since 2026-06-09
 */
class AdminCommentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdminSessionService adminSessionService = mock(AdminSessionService.class);
    private final WalineGatewayClient walineGatewayClient = mock(WalineGatewayClient.class);
    private final AnalyticsContentIndexService contentIndexService = mock(AnalyticsContentIndexService.class);
    private final IpRegionService ipRegionService = mock(IpRegionService.class);
    private AdminCommentService service;

    @BeforeEach
    void setUp() {
        when(adminSessionService.findWalineToken("admin")).thenReturn(Optional.of("waline-token"));
        when(contentIndexService.loadPostMap()).thenReturn(Map.of(
                "/thoughts/测试.html",
                new AnalyticsContentIndexService.PostInfo("/thoughts/测试.html", "测试文章", java.util.List.of())
        ));
        service = new AdminCommentService(
                adminSessionService,
                walineGatewayClient,
                objectMapper,
                new CommentTextNormalizer(),
                contentIndexService,
                new AnalyticsPathPolicy(),
                ipRegionService
        );
    }

    @Test
    void readsAdminCommentPageFromGateway() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {
                  "page": 2,
                  "totalPages": 4,
                  "waitingCount": 1,
                  "spamCount": 2,
                  "data": [
                    {
                      "objectId": "comment-1",
                      "nick": "访客",
                      "orig": "<p>正文</p>",
                      "url": "/thoughts/%E6%B5%8B%E8%AF%95.html",
                      "time": 1710000000000,
                      "status": "approved",
                      "user_id": "user-1",
                      "type": "guest"
                    }
                  ]
                }
                """);
        when(walineGatewayClient.fetchAdminComments("waline-token", 2, "approved", "正文"))
                .thenReturn(response);
        when(walineGatewayClient.fetchApprovedCommentCount()).thenReturn(7);

        AdminCommentListDto result = service.list("admin", "approved", "正文", 2);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(4);
        assertThat(result.totalCount()).isEqualTo(10);
        assertThat(result.data()).hasSize(1);
        AdminCommentItemDto comment = result.data().get(0);
        assertThat(comment.comment()).isEqualTo("正文");
        assertThat(comment.articleTitle()).isEqualTo("测试文章");
        assertThat(comment.url()).isEqualTo("/thoughts/测试.html");
        assertThat(comment.createdAt()).isNotBlank();
        assertThat(comment.userId()).isEqualTo("user-1");
        assertThat(comment.userType()).isEqualTo("guest");
    }

    @Test
    void unwrapsUpdatedCommentResponse() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {
                  "objectId": "comment-1",
                  "nick": "访客",
                  "orig": "更新后的正文",
                  "url": "/thoughts/测试.html",
                  "status": "approved"
                }
                """);
        when(walineGatewayClient.updateAdminComment(
                eq("waline-token"),
                eq("comment-1"),
                any(JsonNode.class)
        )).thenReturn(response);

        AdminCommentItemDto result = service.update(
                "admin",
                "comment-1",
                new AdminCommentUpdateRequest("更新后的正文", null, null)
        );

        assertThat(result.id()).isEqualTo("comment-1");
        assertThat(result.comment()).isEqualTo("更新后的正文");
    }

    @Test
    void delegatesDeleteToWalineGateway() {
        service.delete("admin", "comment-1");

        verify(walineGatewayClient).deleteAdminComment("waline-token", "comment-1");
    }
}
