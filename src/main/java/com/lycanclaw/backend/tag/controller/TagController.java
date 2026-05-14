package com.lycanclaw.backend.tag.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    @GetMapping("/thoughts")
    public ApiResponse<Map<String, Object>> listThoughtTags() {
        return ApiResponse.ok(Map.of(
                "tags", List.of(),
                "note", "待实现：聚合随想文章标签"
        ));
    }

    @GetMapping("/thoughts/filter")
    public ApiResponse<Map<String, Object>> filterThoughtsByTag(@RequestParam("tag") String tag) {
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
