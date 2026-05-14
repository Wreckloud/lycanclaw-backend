package com.lycanclaw.backend.analytics.controller;

import com.lycanclaw.backend.analytics.dto.DailyContributionResponse;
import com.lycanclaw.backend.analytics.service.ContributionService;
import com.lycanclaw.backend.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contributions")
public class ContributionController {

    private final ContributionService contributionService;

    public ContributionController(ContributionService contributionService) {
        this.contributionService = contributionService;
    }

    @GetMapping("/daily")
    public ApiResponse<DailyContributionResponse> dailyContributions(
            @RequestParam(name = "days", required = false) Integer days
    ) {
        return ApiResponse.ok(contributionService.getDailyContributions(days));
    }
}
