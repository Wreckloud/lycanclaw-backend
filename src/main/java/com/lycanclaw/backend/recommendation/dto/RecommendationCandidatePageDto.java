package com.lycanclaw.backend.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 推荐候选文章分页结果。
 * 用于管理端按标题搜索和分页选择手动推荐文章。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "推荐候选文章分页结果")
public record RecommendationCandidatePageDto(
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<RecommendationPostDto> items
) {
}
