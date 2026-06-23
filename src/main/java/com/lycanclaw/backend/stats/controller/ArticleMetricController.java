package com.lycanclaw.backend.stats.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.stats.dto.ArticleMetricDto;
import com.lycanclaw.backend.stats.service.ArticleMetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开文章指标查询接口。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
@Tag(name = "文章指标", description = "查询文章浏览量和评论数快照")
@RestController
@RequestMapping("/api/article-metrics")
public class ArticleMetricController {

    private final ArticleMetricService articleMetricService;

    public ArticleMetricController(ArticleMetricService articleMetricService) {
        this.articleMetricService = articleMetricService;
    }

    @Operation(summary = "查询单篇文章指标")
    @GetMapping
    public ApiResponse<ArticleMetricDto> find(
            @Parameter(description = "文章路径", required = true)
            @RequestParam("path") String path
    ) {
        return ApiResponse.ok(articleMetricService.find(path));
    }
}
