package com.lycanclaw.backend.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * @Description 阅读量上报请求
 * @Author Wreckloud
 * @Date 2026-05-15
 */
@Schema(description = "阅读量上报请求")
public record PageviewUpdateRequest(
        @NotBlank(message = "path 不能为空")
        @Schema(description = "文章路径，例如 /thoughts/xxx.html", requiredMode = Schema.RequiredMode.REQUIRED)
        String path
) {
}
