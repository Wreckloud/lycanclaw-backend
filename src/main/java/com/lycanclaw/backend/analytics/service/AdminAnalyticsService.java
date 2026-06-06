package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.AdminAnalyticsSummaryDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticleMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsTagMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsTrendPointDto;
import com.lycanclaw.backend.analytics.dto.EncouragementEventDto;
import com.lycanclaw.backend.analytics.dto.EncouragementSummaryDto;
import com.lycanclaw.backend.analytics.dto.EncouragementVisitorMetricDto;
import com.lycanclaw.backend.analytics.entity.AnalyticsVisitEntity;
import com.lycanclaw.backend.analytics.entity.EncouragementEventEntity;
import com.lycanclaw.backend.analytics.repository.AnalyticsVisitRepository;
import com.lycanclaw.backend.analytics.repository.EncouragementEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台写作洞察服务。
 * 聚合访问统计、文章停留、标签关注和催更数据，供管理端首页图表展示。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Service
public class AdminAnalyticsService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AnalyticsVisitRepository visitRepository;
    private final EncouragementEventRepository encouragementRepository;
    private final AnalyticsContentIndexService contentIndexService;
    private final ZoneId zoneId;

    public AdminAnalyticsService(
            AnalyticsVisitRepository visitRepository,
            EncouragementEventRepository encouragementRepository,
            AnalyticsContentIndexService contentIndexService,
            @Value("${lycan.system.zone-id:Asia/Shanghai}") String zoneId
    ) {
        this.visitRepository = visitRepository;
        this.encouragementRepository = encouragementRepository;
        this.contentIndexService = contentIndexService;
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * 构建后台首页写作洞察摘要，默认统计最近 30 天。
     */
    public AdminAnalyticsSummaryDto summary() {
        return summary(DEFAULT_DAYS);
    }

    /**
     * 按指定天数构建写作洞察摘要。
     */
    public AdminAnalyticsSummaryDto summary(int days) {
        int safeDays = normalizeDays(days);
        OffsetDateTime since = OffsetDateTime.now(zoneId).minusDays(safeDays - 1L).toLocalDate().atStartOfDay(zoneId).toOffsetDateTime();
        List<AnalyticsVisitEntity> visits = visitRepository.findByStartedAtAfter(since);
        List<EncouragementEventEntity> encouragements = encouragementRepository.findByCreatedAtAfter(since);
        List<AnalyticsArticleMetricDto> articles = buildArticleMetrics(visits, encouragements);
        return new AdminAnalyticsSummaryDto(
                safeDays,
                visits.size(),
                countUniqueVisitors(visits),
                averageDurationSeconds(visits),
                encouragementRepository.sumAllDelta(),
                buildTrend(safeDays, since, visits, encouragements),
                articles.stream().sorted(Comparator.comparingLong(AnalyticsArticleMetricDto::visits).reversed()).limit(8).toList(),
                articles.stream().sorted(Comparator.comparingDouble(AnalyticsArticleMetricDto::averageDurationSeconds).reversed()).limit(8).toList(),
                buildTagMetrics(visits).stream().limit(10).toList(),
                encouragementSummary(encouragements)
        );
    }

    /**
     * 返回文章访问排行，供管理端独立接口使用。
     */
    public List<AnalyticsArticleMetricDto> articleMetrics(int days) {
        int safeDays = normalizeDays(days);
        OffsetDateTime since = OffsetDateTime.now(zoneId).minusDays(safeDays - 1L).toLocalDate().atStartOfDay(zoneId).toOffsetDateTime();
        return buildArticleMetrics(
                visitRepository.findByStartedAtAfter(since),
                encouragementRepository.findByCreatedAtAfter(since)
        );
    }

    /**
     * 返回按 tag 聚合后的访问关注数据。
     */
    public List<AnalyticsTagMetricDto> tagMetrics(int days) {
        int safeDays = normalizeDays(days);
        OffsetDateTime since = OffsetDateTime.now(zoneId).minusDays(safeDays - 1L).toLocalDate().atStartOfDay(zoneId).toOffsetDateTime();
        return buildTagMetrics(visitRepository.findByStartedAtAfter(since));
    }

    /**
     * 返回催更统计摘要。
     */
    public EncouragementSummaryDto encouragementSummary(int days) {
        int safeDays = normalizeDays(days);
        OffsetDateTime since = OffsetDateTime.now(zoneId).minusDays(safeDays - 1L).toLocalDate().atStartOfDay(zoneId).toOffsetDateTime();
        return encouragementSummary(encouragementRepository.findByCreatedAtAfter(since));
    }

    private List<AnalyticsTrendPointDto> buildTrend(
            int days,
            OffsetDateTime since,
            List<AnalyticsVisitEntity> visits,
            List<EncouragementEventEntity> encouragements
    ) {
        Map<LocalDate, TrendAccumulator> trend = new LinkedHashMap<>();
        LocalDate start = since.toLocalDate();
        for (int i = 0; i < days; i++) {
            trend.put(start.plusDays(i), new TrendAccumulator());
        }
        for (AnalyticsVisitEntity visit : visits) {
            LocalDate date = visit.getStartedAt().atZoneSameInstant(zoneId).toLocalDate();
            TrendAccumulator accumulator = trend.get(date);
            if (accumulator != null) {
                accumulator.visits++;
                accumulator.visitors.add(visitorKey(visit.getVisitorId(), visit.getIp()));
            }
        }
        for (EncouragementEventEntity event : encouragements) {
            LocalDate date = event.getCreatedAt().atZoneSameInstant(zoneId).toLocalDate();
            TrendAccumulator accumulator = trend.get(date);
            if (accumulator != null) {
                accumulator.encouragements += event.getDelta();
            }
        }
        return trend.entrySet().stream()
                .map(entry -> new AnalyticsTrendPointDto(
                        entry.getKey().format(DATE_FORMATTER),
                        entry.getValue().visits,
                        entry.getValue().visitors.size(),
                        entry.getValue().encouragements
                ))
                .toList();
    }

    private List<AnalyticsArticleMetricDto> buildArticleMetrics(
            List<AnalyticsVisitEntity> visits,
            List<EncouragementEventEntity> encouragements
    ) {
        Map<String, ArticleAccumulator> byPath = new LinkedHashMap<>();
        for (AnalyticsVisitEntity visit : visits) {
            if (!"article".equalsIgnoreCase(visit.getPageType())) {
                continue;
            }
            ArticleAccumulator accumulator = byPath.computeIfAbsent(visit.getPath(), key -> new ArticleAccumulator(visit.getPath(), visit.getTitle()));
            accumulator.visits++;
            accumulator.totalDurationMs += Math.max(0, visit.getDurationMs());
            accumulator.visitors.add(visitorKey(visit.getVisitorId(), visit.getIp()));
        }
        for (EncouragementEventEntity event : encouragements) {
            ArticleAccumulator accumulator = byPath.get(event.getPath());
            if (accumulator != null) {
                accumulator.encouragements += event.getDelta();
            }
        }
        return byPath.values().stream()
                .map(this::toArticleMetric)
                .sorted(Comparator.comparingLong(AnalyticsArticleMetricDto::visits).reversed())
                .toList();
    }

    private List<AnalyticsTagMetricDto> buildTagMetrics(List<AnalyticsVisitEntity> visits) {
        Map<String, AnalyticsContentIndexService.PostInfo> posts = contentIndexService.loadPostMap();
        Map<String, TagAccumulator> byTag = new HashMap<>();
        for (AnalyticsVisitEntity visit : visits) {
            AnalyticsContentIndexService.PostInfo post = posts.get(visit.getPath());
            if (post == null) {
                continue;
            }
            for (String tag : post.tags()) {
                TagAccumulator accumulator = byTag.computeIfAbsent(tag, TagAccumulator::new);
                accumulator.visits++;
                accumulator.totalDurationMs += Math.max(0, visit.getDurationMs());
                accumulator.articlePaths.add(visit.getPath());
            }
        }
        return byTag.values().stream()
                .map(accumulator -> new AnalyticsTagMetricDto(
                        accumulator.tag,
                        accumulator.visits,
                        accumulator.articlePaths.size(),
                        roundSeconds(accumulator.totalDurationMs, accumulator.visits)
                ))
                .sorted(Comparator.comparingLong(AnalyticsTagMetricDto::visits).reversed())
                .toList();
    }

    private EncouragementSummaryDto encouragementSummary(List<EncouragementEventEntity> events) {
        OffsetDateTime todayStart = OffsetDateTime.now(zoneId).toLocalDate().atStartOfDay(zoneId).toOffsetDateTime();
        OffsetDateTime weekStart = todayStart.minusDays(6);
        Map<String, VisitorAccumulator> visitors = new LinkedHashMap<>();
        Map<String, ArticleAccumulator> pages = new LinkedHashMap<>();
        for (EncouragementEventEntity event : events) {
            String visitorKey = visitorKey(event.getVisitorId(), event.getIp());
            VisitorAccumulator visitor = visitors.computeIfAbsent(visitorKey, key -> new VisitorAccumulator(event.getVisitorId(), event.getIp()));
            visitor.totalDelta += event.getDelta();
            if (visitor.lastAt == null || event.getCreatedAt().isAfter(visitor.lastAt)) {
                visitor.lastAt = event.getCreatedAt();
            }
            ArticleAccumulator page = pages.computeIfAbsent(event.getPath(), key -> new ArticleAccumulator(event.getPath(), event.getTitle()));
            page.encouragements += event.getDelta();
        }
        return new EncouragementSummaryDto(
                encouragementRepository.sumAllDelta(),
                encouragementRepository.sumDeltaAfter(todayStart),
                encouragementRepository.sumDeltaAfter(weekStart),
                visitors.values().stream()
                        .map(this::toVisitorMetric)
                        .sorted(Comparator.comparingLong(EncouragementVisitorMetricDto::totalDelta).reversed())
                        .limit(8)
                        .toList(),
                pages.values().stream()
                        .map(this::toArticleMetric)
                        .sorted(Comparator.comparingLong(AnalyticsArticleMetricDto::encouragements).reversed())
                        .limit(8)
                        .toList(),
                encouragementRepository.findTop20ByOrderByCreatedAtDesc().stream()
                        .map(this::toEventDto)
                        .toList()
        );
    }

    private AnalyticsArticleMetricDto toArticleMetric(ArticleAccumulator accumulator) {
        return new AnalyticsArticleMetricDto(
                accumulator.path,
                accumulator.title,
                accumulator.visits,
                accumulator.visitors.size(),
                roundSeconds(accumulator.totalDurationMs, accumulator.visits),
                accumulator.totalDurationMs / 1000L,
                accumulator.encouragements
        );
    }

    private EncouragementVisitorMetricDto toVisitorMetric(VisitorAccumulator accumulator) {
        return new EncouragementVisitorMetricDto(
                accumulator.visitorId,
                accumulator.ip,
                accumulator.totalDelta,
                formatOffset(accumulator.lastAt)
        );
    }

    private EncouragementEventDto toEventDto(EncouragementEventEntity event) {
        return new EncouragementEventDto(
                event.getVisitorId(),
                event.getIp(),
                event.getDelta(),
                event.getPath(),
                event.getTitle(),
                formatOffset(event.getCreatedAt())
        );
    }

    private String formatOffset(OffsetDateTime value) {
        return value == null ? "" : value.atZoneSameInstant(zoneId).toOffsetDateTime().toString();
    }

    private long countUniqueVisitors(List<AnalyticsVisitEntity> visits) {
        Set<String> visitors = new HashSet<>();
        for (AnalyticsVisitEntity visit : visits) {
            visitors.add(visitorKey(visit.getVisitorId(), visit.getIp()));
        }
        return visitors.size();
    }

    private double averageDurationSeconds(List<AnalyticsVisitEntity> visits) {
        if (visits.isEmpty()) {
            return 0;
        }
        long total = visits.stream().mapToLong(AnalyticsVisitEntity::getDurationMs).sum();
        return roundSeconds(total, visits.size());
    }

    private double roundSeconds(long durationMs, long count) {
        if (count <= 0) {
            return 0;
        }
        double seconds = durationMs / 1000.0 / count;
        return Math.round(seconds * 10.0) / 10.0;
    }

    private int normalizeDays(int days) {
        if (days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private String visitorKey(String visitorId, String ip) {
        String visitor = visitorId == null || visitorId.isBlank() ? "anonymous" : visitorId;
        String address = ip == null ? "" : ip;
        return visitor + "@" + address;
    }

    private static final class TrendAccumulator {
        private long visits;
        private long encouragements;
        private final Set<String> visitors = new HashSet<>();
    }

    private static final class ArticleAccumulator {
        private final String path;
        private final String title;
        private long visits;
        private long totalDurationMs;
        private long encouragements;
        private final Set<String> visitors = new HashSet<>();

        private ArticleAccumulator(String path, String title) {
            this.path = path;
            this.title = title == null || title.isBlank() ? path : title;
        }
    }

    private static final class TagAccumulator {
        private final String tag;
        private long visits;
        private long totalDurationMs;
        private final Set<String> articlePaths = new HashSet<>();

        private TagAccumulator(String tag) {
            this.tag = tag;
        }
    }

    private static final class VisitorAccumulator {
        private final String visitorId;
        private final String ip;
        private long totalDelta;
        private OffsetDateTime lastAt;

        private VisitorAccumulator(String visitorId, String ip) {
            this.visitorId = visitorId == null ? "" : visitorId;
            this.ip = ip == null ? "" : ip;
        }
    }
}
