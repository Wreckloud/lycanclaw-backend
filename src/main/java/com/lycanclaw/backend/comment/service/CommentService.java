package com.lycanclaw.backend.comment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Description 评论聚合服务（后端统一入口）
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Service
public class CommentService {

    private final WalineGatewayClient walineGatewayClient;
    private final ObjectMapper objectMapper;

    public CommentService(WalineGatewayClient walineGatewayClient, ObjectMapper objectMapper) {
        this.walineGatewayClient = walineGatewayClient;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> recentComments(int limit) {
        JsonNode node = walineGatewayClient.fetchRecentComments(limit);
        List<Map<String, Object>> result = new ArrayList<>();

        if (node == null || node.isNull()) {
            return result;
        }

        JsonNode arrayNode = node.isArray() ? node : node.path("data");
        if (!arrayNode.isArray()) {
            return result;
        }

        for (JsonNode item : arrayNode) {
            Map<String, Object> row = objectMapper.convertValue(item, new TypeReference<>() {
            });
            result.add(row);
        }

        return result;
    }

    public int commentCount(String path) {
        String normalizedPath = normalizePath(path);
        return walineGatewayClient.fetchCommentCount(normalizedPath);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 参数不能为空");
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
