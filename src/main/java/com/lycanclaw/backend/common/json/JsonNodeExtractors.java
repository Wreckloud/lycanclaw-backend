package com.lycanclaw.backend.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * JSON 节点提取工具。
 * 用于从不稳定的上游响应结构中安全提取文本、数字和数组字段。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class JsonNodeExtractors {

    /**
     * 递归查找首个非空文本字段。
     */
    public Optional<String> findText(JsonNode node, String key) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (node.has(key) && !node.get(key).isNull()) {
            String value = node.get(key).asText("");
            if (!value.isBlank()) {
                return Optional.of(value);
            }
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                Optional<String> child = findText(entry.getValue(), key);
                if (child.isPresent()) {
                    return child;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<String> parsed = findText(child, key);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 递归查找整数值，无法解析返回 empty。
     */
    public Optional<Integer> findInt(JsonNode node, String key) {
        Optional<String> text = findText(node, key);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(text.get()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 递归查找数组字段。
     */
    public Optional<JsonNode> findArray(JsonNode node, String key) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (node.has(key) && node.get(key).isArray()) {
            return Optional.of(node.get(key));
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                Optional<JsonNode> child = findArray(entry.getValue(), key);
                if (child.isPresent()) {
                    return child;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<JsonNode> parsed = findArray(child, key);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        }
        return Optional.empty();
    }
}
