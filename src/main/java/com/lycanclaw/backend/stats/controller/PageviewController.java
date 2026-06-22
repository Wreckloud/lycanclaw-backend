package com.lycanclaw.backend.stats.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.stats.dto.PageviewUpdateRequest;
import com.lycanclaw.backend.stats.service.PageviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读量接口控制器。
 * 用于提供阅读量相关 REST 接口。
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/stats")
@Tag(name = "阅读统计", description = "文章阅读量查询与上报")
public class PageviewController {

    private final PageviewService pageviewService;

    public PageviewController(PageviewService pageviewService) {
        this.pageviewService = pageviewService;
    }

    @Operation(summary = "上报文章阅读量")
    @PostMapping("/pageview")
    public ApiResponse<Integer> updatePageview(@Valid @RequestBody PageviewUpdateRequest request) {
        return ApiResponse.ok(pageviewService.updatePageview(request.path()));
    }
}
