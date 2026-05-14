package com.lycanclaw.backend.analytics.dto;

public record DailyContributionItem(
        String date,
        int additions,
        int deletions,
        int total
) {
}
