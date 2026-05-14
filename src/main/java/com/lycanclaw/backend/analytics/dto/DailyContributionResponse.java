package com.lycanclaw.backend.analytics.dto;

import java.util.List;

public record DailyContributionResponse(
        String generatedAt,
        String timezone,
        String metric,
        int days,
        List<String> scope,
        List<DailyContributionItem> data
) {
}
