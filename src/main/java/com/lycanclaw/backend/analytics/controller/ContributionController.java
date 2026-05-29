package com.lycanclaw.backend.analytics.controller;

import com.lycanclaw.backend.analytics.dto.DailyContributionResponse;
import com.lycanclaw.backend.analytics.service.ContributionService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ContributionController：
 * 处理Contribution相关接口请求。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/contributions")
@Tag(name = "数据统计", description = "博客贡献热力图统计接口")
public class ContributionController {

    private final ContributionService contributionService;

    public ContributionController(ContributionService contributionService) {
        this.contributionService = contributionService;
    }

    /**
     * 查询日贡献热力图数据，统计口径为 additions + deletions。
     */
    @Operation(summary = "查询日贡献数据")
    @GetMapping("/daily")
    public ApiResponse<DailyContributionResponse> dailyContributions(
            @Parameter(description = "统计天数，默认读取配置")
            @RequestParam(name = "days", required = false) Integer days
    ) {
        return ApiResponse.ok(contributionService.getDailyContributions(days));
    }
}
