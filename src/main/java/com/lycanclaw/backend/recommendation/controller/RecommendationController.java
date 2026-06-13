package com.lycanclaw.backend.recommendation.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationManualConfigUpdateRequest;
import com.lycanclaw.backend.recommendation.dto.RecommendationPostDto;
import com.lycanclaw.backend.recommendation.dto.RecommendationCandidatePageDto;
import com.lycanclaw.backend.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 推荐接口控制器。
 * 用于提供推荐相关 REST 接口。
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "推荐阅读", description = "热门推荐与手动推荐配置")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Operation(summary = "获取推荐阅读")
    @GetMapping
    public ApiResponse<List<RecommendationPostDto>> list(
            @Parameter(description = "排除当前文章路径，例如 /thoughts/xxx.html")
            @RequestParam(value = "excludePath", required = false) String excludePath,
            @Parameter(description = "返回条数，默认 5，最大 20")
            @RequestParam(value = "limit", defaultValue = "5") int limit
    ) {
        return ApiResponse.ok(recommendationService.listRecommendations(excludePath, limit));
    }

    @Operation(summary = "读取手动推荐配置（管理员）")
    @GetMapping("/admin/config")
    public ApiResponse<RecommendationManualConfigDto> getManualConfig() {
        return ApiResponse.ok(recommendationService.getManualConfig());
    }

    @Operation(summary = "获取候选文章列表（管理员）")
    @GetMapping("/admin/candidates")
    public ApiResponse<RecommendationCandidatePageDto> listCandidates(
            @Parameter(description = "标题关键词")
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @Parameter(description = "页码，从 1 开始")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页条数，默认 12")
            @RequestParam(value = "pageSize", defaultValue = "12") int pageSize
    ) {
        return ApiResponse.ok(recommendationService.listCandidates(keyword, page, pageSize));
    }

    @Operation(summary = "更新手动推荐配置（管理员）")
    @PutMapping("/admin/config")
    public ApiResponse<RecommendationManualConfigDto> updateManualConfig(
            @Valid @RequestBody RecommendationManualConfigUpdateRequest request
    ) {
        return ApiResponse.ok(recommendationService.updateManualConfig(request.manualUrls(), request.excludedUrls()));
    }
}
