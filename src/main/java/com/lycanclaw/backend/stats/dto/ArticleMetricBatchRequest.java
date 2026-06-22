package com.lycanclaw.backend.stats.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ArticleMetricBatchRequest(
        @NotEmpty
        @Size(max = 200)
        List<String> paths
) {
}
