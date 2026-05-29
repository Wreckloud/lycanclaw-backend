package com.lycanclaw.backend.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * RecommendationManualConfigUpdateRequest：
 * RecommendationManualConfigUpdateRequest的数据传输模型。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Schema(description = "手动推荐配置更新请求")
public record RecommendationManualConfigUpdateRequest(
        @NotNull(message = "manualUrls 不能为空")
        @Schema(description = "手动置顶文章 URL 列表")
        List<String> manualUrls
) {
}
