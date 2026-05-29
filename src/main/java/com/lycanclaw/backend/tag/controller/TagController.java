package com.lycanclaw.backend.tag.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.tag.dto.ThoughtTagFilterResponseDto;
import com.lycanclaw.backend.tag.dto.ThoughtTagsResponseDto;
import com.lycanclaw.backend.tag.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TagController：
 * 处理Tag相关接口请求。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/tags")
@Tag(name = "标签", description = "随想标签聚合与筛选接口")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 返回随想标签集合与文章计数。
     */
    @Operation(summary = "查询随想标签列表")
    @GetMapping("/thoughts")
    public ApiResponse<ThoughtTagsResponseDto> listThoughtTags() {
        return ApiResponse.ok(tagService.listThoughtTags());
    }

    /**
     * 按标签筛选随想文章，支持服务端分页。
     */
    @Operation(summary = "按标签筛选随想")
    @GetMapping("/thoughts/filter")
    public ApiResponse<ThoughtTagFilterResponseDto> filterThoughtsByTag(
            @Parameter(description = "标签名；不传表示全部")
            @RequestParam(value = "tag", required = false) String tag,
            @Parameter(description = "页码，默认 1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量，默认 10，最大 50")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(tagService.filterThoughtsByTag(tag, page, pageSize));
    }
}
