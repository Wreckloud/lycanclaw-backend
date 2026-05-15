package com.lycanclaw.backend.tag.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * @Description 标签模块接口占位控制器
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@RestController
@RequestMapping("/api/tags")
@Tag(name = "标签", description = "随想标签聚合与筛选接口（占位）")
public class TagController {

    /**
     * 返回随想标签集合（当前为占位响应，后续接真实聚合逻辑）。
     */
    @Operation(summary = "查询随想标签列表（占位）")
    @GetMapping("/thoughts")
    public ApiResponse<Map<String, Object>> listThoughtTags() {
        return ApiResponse.ok(Map.of(
                "tags", List.of(),
                "note", "待实现：聚合随想文章标签"
        ));
    }

    /**
     * 按标签过滤随想文章（当前为占位响应）。
     */
    @Operation(summary = "按标签筛选随想（占位）")
    @GetMapping("/thoughts/filter")
    public ApiResponse<Map<String, Object>> filterThoughtsByTag(
            @Parameter(description = "标签名", required = true)
            @RequestParam("tag") String tag
    ) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag 参数不能为空");
        }
        return ApiResponse.ok(Map.of(
                "tag", tag,
                "posts", List.of(),
                "note", "待实现：按标签返回随想文章列表"
        ));
    }
}
