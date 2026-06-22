package com.lycanclaw.backend.stats.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.stats.dto.ArticleMetricBatchRequest;
import com.lycanclaw.backend.stats.dto.ArticleMetricDto;
import com.lycanclaw.backend.stats.service.ArticleMetricService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/article-metrics")
public class ArticleMetricController {

    private final ArticleMetricService articleMetricService;

    public ArticleMetricController(ArticleMetricService articleMetricService) {
        this.articleMetricService = articleMetricService;
    }

    @GetMapping
    public ApiResponse<ArticleMetricDto> find(@RequestParam("path") String path) {
        return ApiResponse.ok(articleMetricService.find(path));
    }

    @PostMapping("/batch")
    public ApiResponse<List<ArticleMetricDto>> findBatch(@Valid @RequestBody ArticleMetricBatchRequest request) {
        return ApiResponse.ok(articleMetricService.findBatch(request.paths()));
    }
}
