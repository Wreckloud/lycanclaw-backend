package com.lycanclaw.backend.stats.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.stats.dto.PageviewUpdateRequest;
import com.lycanclaw.backend.stats.service.PageviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读量统计接口
 *
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

    @Operation(summary = "查询文章阅读量")
    @GetMapping("/pageview")
    public ApiResponse<Integer> getPageview(
            @Parameter(description = "文章路径，例如 /thoughts/xxx.html", required = true)
            @RequestParam("path") String path
    ) {
        return ApiResponse.ok(pageviewService.getPageview(path));
    }

    @Operation(summary = "上报文章阅读量")
    @PostMapping("/pageview")
    public ApiResponse<Integer> updatePageview(@Valid @RequestBody PageviewUpdateRequest request) {
        return ApiResponse.ok(pageviewService.updatePageview(request.path()));
    }
}
