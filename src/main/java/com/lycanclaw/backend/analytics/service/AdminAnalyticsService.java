package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.AdminAnalyticsSummaryDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticleDetailDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticleMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticlePageDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsNamedMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsRecentVisitDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsTagMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsTrendPointDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsVisitorActivityDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsVisitorProfileDto;
import com.lycanclaw.backend.analytics.dto.EncouragementEventDto;
import com.lycanclaw.backend.analytics.dto.EncouragementSummaryDto;
import com.lycanclaw.backend.analytics.dto.EncouragementVisitorMetricDto;
import com.lycanclaw.backend.analytics.dto.MusicAnalyticsSummaryDto;
import com.lycanclaw.backend.analytics.dto.MusicListenRecordDto;
import com.lycanclaw.backend.analytics.dto.MusicSongMetricDto;
import com.lycanclaw.backend.analytics.entity.AnalyticsVisitEntity;
import com.lycanclaw.backend.analytics.entity.AnalyticsVisitorIdentityEntity;
import com.lycanclaw.backend.analytics.entity.EncouragementEventEntity;
import com.lycanclaw.backend.analytics.entity.MusicListenSessionEntity;
import com.lycanclaw.backend.analytics.repository.AnalyticsVisitRepository;
import com.lycanclaw.backend.analytics.repository.AnalyticsVisitorIdentityRepository;
import com.lycanclaw.backend.analytics.repository.EncouragementEventRepository;
import com.lycanclaw.backend.analytics.repository.MusicListenSessionRepository;
import com.lycanclaw.backend.recommendation.entity.RecommendationMetricEntity;
import com.lycanclaw.backend.recommendation.service.RecommendationAggregationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 后台内容洞察服务。
 * 聚合文章、访客、主题、催更和音乐收听数据，供统一管理控制台查询。
 * @author Wreckloud
 * @since 2026-06-04
 */
@Service
public class AdminAnalyticsService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;
    private static final int COMPLETION_PERCENT = 90;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AnalyticsVisitRepository visitRepository;
    private final EncouragementEventRepository encouragementRepository;
    private final AnalyticsVisitorIdentityRepository identityRepository;
    private final MusicListenSessionRepository musicRepository;
    private final AnalyticsContentIndexService contentIndexService;
    private final AnalyticsPathPolicy pathPolicy;
    private final IpRegionService ipRegionService;
    private final VisitorIdentityService visitorIdentityService;
    private final RecommendationAggregationService recommendationAggregationService;
    private final ZoneId zoneId;

    public AdminAnalyticsService(
            AnalyticsVisitRepository visitRepository,
            EncouragementEventRepository encouragementRepository,
            AnalyticsVisitorIdentityRepository identityRepository,
            MusicListenSessionRepository musicRepository,
            AnalyticsContentIndexService contentIndexService,
            AnalyticsPathPolicy pathPolicy,
            IpRegionService ipRegionService,
            VisitorIdentityService visitorIdentityService,
            RecommendationAggregationService recommendationAggregationService,
            @Value("${lycan.system.zone-id:Asia/Shanghai}") String zoneId
    ) {
        this.visitRepository = visitRepository;
        this.encouragementRepository = encouragementRepository;
        this.identityRepository = identityRepository;
        this.musicRepository = musicRepository;
        this.contentIndexService = contentIndexService;
        this.pathPolicy = pathPolicy;
        this.ipRegionService = ipRegionService;
        this.visitorIdentityService = visitorIdentityService;
        this.recommendationAggregationService = recommendationAggregationService;
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * 构建后台首页摘要，默认统计最近 30 天。
     */
    public AdminAnalyticsSummaryDto summary() {
        return summary(DEFAULT_DAYS);
    }

    /**
     * 按指定天数构建访问、主题和催更摘要。
     */
    public AdminAnalyticsSummaryDto summary(int days) {
        int safeDays = normalizeDays(days);
        OffsetDateTime since = since(safeDays);
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
                articles.stream().limit(8).toList(),
                articles.stream()
                        .sorted(Comparator.comparingDouble(AnalyticsArticleMetricDto::averageDurationSeconds).reversed())
                        .limit(8)
                        .toList(),
                buildTagMetrics(visits).stream().limit(10).toList(),
                encouragementSummary(encouragements)
        );
    }

    /**
     * 返回兼容旧管理端使用的文章指标列表。
     */
    public List<AnalyticsArticleMetricDto> articleMetrics(int days) {
        return buildArticleMetrics(
                visitRepository.findByStartedAtAfter(since(normalizeDays(days))),
                encouragementRepository.findByCreatedAtAfter(since(normalizeDays(days)))
        );
    }

    /**
     * 按标题关键词、排序和分页条件返回文章洞察。
     */
    public AnalyticsArticlePageDto articleMetrics(
            int days,
            String keyword,
            String sort,
            int page,
            int pageSize
    ) {
        int safeDays = normalizeDays(days);
        OffsetDateTime since = since(safeDays);
        List<AnalyticsVisitEntity> visits = visitRepository.findByStartedAtAfter(since);
        List<AnalyticsArticleMetricDto> filtered = buildArticleMetrics(
                visits,
                encouragementRepository.findByCreatedAtAfter(since)
        ).stream()
                .filter(item -> matchesKeyword(item, keyword))
                .sorted(articleComparator(sort))
                .toList();
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) safePageSize));
        int safePage = Math.max(1, Math.min(page, totalPages));
        int from = Math.min(filtered.size(), (safePage - 1) * safePageSize);
        int to = Math.min(filtered.size(), from + safePageSize);
        return new AnalyticsArticlePageDto(
                safePage,
                safePageSize,
                filtered.size(),
                totalPages,
                filtered.subList(from, to),
                buildRecentArticleVisits(visits)
        );
    }

    /**
     * 返回单篇文章的指标、趋势、来源和访客明细。
     */
    public AnalyticsArticleDetailDto articleDetail(int days, String path) {
        int safeDays = normalizeDays(days);
        String normalizedPath = pathPolicy.normalizePath(path);
        OffsetDateTime since = since(safeDays);
        List<AnalyticsVisitEntity> visits = visitRepository.findByStartedAtAfter(since).stream()
                .filter(visit -> normalizedPath.equals(pathPolicy.normalizePath(visit.getPath())))
                .toList();
        List<EncouragementEventEntity> encouragements = encouragementRepository.findByCreatedAtAfter(since).stream()
                .filter(event -> normalizedPath.equals(pathPolicy.normalizePath(event.getPath())))
                .toList();
        AnalyticsArticleMetricDto metric = buildArticleMetrics(visits, encouragements).stream()
                .findFirst()
                .orElseGet(() -> emptyArticleMetric(normalizedPath));
        return new AnalyticsArticleDetailDto(
                metric,
                buildTrend(safeDays, since, visits, encouragements),
                namedDistribution(visits, visit -> referrerLabel(visit.getReferrer()), false),
                buildArticleVisitors(visits)
        );
    }

    /**
     * 返回按文章标签聚合后的主题关注数据。
     */
    public List<AnalyticsTagMetricDto> tagMetrics(int days) {
        return buildTagMetrics(visitRepository.findByStartedAtAfter(since(normalizeDays(days))));
    }

    /**
     * 返回催更统计与访客排行榜。
     */
    public EncouragementSummaryDto encouragementSummary(int days) {
        return encouragementSummary(
                encouragementRepository.findByCreatedAtAfter(since(normalizeDays(days)))
        );
    }

    /**
     * 返回指定匿名访客的统一画像。
     */
    public AnalyticsVisitorProfileDto visitorProfile(int days, String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            throw new IllegalArgumentException("visitorId 不能为空");
        }
        int safeDays = normalizeDays(days);
        String normalizedVisitorId = visitorId.trim();
        OffsetDateTime since = since(safeDays);
        List<AnalyticsVisitEntity> visits = visitRepository.findByStartedAtAfter(since).stream()
                .filter(visit -> normalizedVisitorId.equals(visit.getVisitorId()))
                .toList();
        List<EncouragementEventEntity> encouragements = encouragementRepository.findByCreatedAtAfter(since).stream()
                .filter(event -> normalizedVisitorId.equals(event.getVisitorId()))
                .toList();
        List<MusicListenSessionEntity> listens = musicRepository.findByStartedAtAfter(since).stream()
                .filter(listen -> normalizedVisitorId.equals(listen.getVisitorId()))
                .toList();
        AnalyticsVisitorIdentityEntity identity = identityRepository.findByVisitorId(normalizedVisitorId).orElse(null);
        String ip = latestIp(visits, encouragements, listens);
        String device = visits.stream()
                .max(Comparator.comparing(AnalyticsVisitEntity::getStartedAt))
                .map(visit -> deviceLabel(visit.getUserAgent()))
                .orElse("");

        return new AnalyticsVisitorProfileDto(
                normalizedVisitorId,
                visitorIdentityService.displayName(identity),
                identity == null ? "" : identity.getAvatar(),
                identity == null ? "" : identity.getProvider(),
                ip,
                ipRegionService.resolve(ip),
                device,
                visits.size(),
                visits.stream().mapToLong(AnalyticsVisitEntity::getDurationMs).sum() / 1000L,
                encouragements.stream().mapToLong(EncouragementEventEntity::getDelta).sum(),
                listens.stream().mapToLong(MusicListenSessionEntity::getListenedMs).sum() / 1000L,
                buildArticleMetrics(visits, encouragements).stream().limit(8).toList(),
                namedDistribution(visits, visit -> referrerLabel(visit.getReferrer()), false),
                namedDistribution(listens, MusicListenSessionEntity::getSongName, true)
        );
    }

    /**
     * 返回音乐播放次数、听众、时长、完成率和热门来源。
     */
    public MusicAnalyticsSummaryDto musicAnalytics(int days) {
        int safeDays = normalizeDays(days);
        List<MusicListenSessionEntity> sessions = musicRepository.findByStartedAtAfter(since(safeDays));
        Map<String, AnalyticsVisitorIdentityEntity> identities = identityMap();
        long completed = sessions.stream().filter(this::isMusicCompleted).count();
        List<MusicListenRecordDto> recent = sessions.stream()
                .sorted(Comparator.comparing(MusicListenSessionEntity::getUpdatedAt).reversed())
                .limit(20)
                .map(item -> {
                    AnalyticsVisitorIdentityEntity identity = identities.get(item.getVisitorId());
                    return new MusicListenRecordDto(
                            item.getVisitorId(),
                            visitorIdentityService.displayName(identity),
                            identity == null ? "" : identity.getAvatar(),
                            item.getSongId(),
                            item.getSongName(),
                            item.getArtist(),
                            item.getPlaybackSource(),
                            item.getUrlSource(),
                            item.getPagePath(),
                            musicProgressPercent(item),
                            isMusicCompleted(item),
                            formatOffset(item.getUpdatedAt())
                    );
                })
                .toList();
        return new MusicAnalyticsSummaryDto(
                safeDays,
                sessions.size(),
                sessions.stream().map(MusicListenSessionEntity::getVisitorId).filter(value -> !value.isBlank()).distinct().count(),
                roundPercent(sessions.stream().mapToDouble(this::musicProgressPercent).average().orElse(0)),
                percentage(completed, sessions.size()),
                buildMusicSongMetrics(sessions),
                namedDistribution(sessions, MusicListenSessionEntity::getPlaybackSource, false),
                namedDistribution(sessions, MusicListenSessionEntity::getUrlSource, false),
                recent
        );
    }

    private List<MusicSongMetricDto> buildMusicSongMetrics(List<MusicListenSessionEntity> sessions) {
        Map<String, List<MusicListenSessionEntity>> grouped = sessions.stream()
                .collect(Collectors.groupingBy(
                        MusicListenSessionEntity::getSongId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return grouped.values().stream()
                .map(items -> {
                    MusicListenSessionEntity sample = items.get(0);
                    long completed = items.stream().filter(this::isMusicCompleted).count();
                    return new MusicSongMetricDto(
                            sample.getSongId(),
                            sample.getSongName(),
                            sample.getArtist(),
                            items.size(),
                            items.stream()
                                    .map(MusicListenSessionEntity::getVisitorId)
                                    .filter(value -> value != null && !value.isBlank())
                                    .distinct()
                                    .count(),
                            Math.round(items.stream().mapToDouble(this::musicProgressPercent).average().orElse(0) * 10.0) / 10.0,
                            percentage(completed, items.size())
                    );
                })
                .sorted(Comparator.comparingLong(MusicSongMetricDto::plays).reversed())
                .limit(10)
                .toList();
    }

    private double musicProgressPercent(MusicListenSessionEntity session) {
        if (session.getDurationMs() <= 0) {
            return session.isCompleted() ? 100 : 0;
        }
        double progress = session.getListenedMs() * 100.0 / session.getDurationMs();
        return Math.round(Math.max(0, Math.min(progress, 100)) * 10.0) / 10.0;
    }

    private boolean isMusicCompleted(MusicListenSessionEntity session) {
        return session.isCompleted() || musicProgressPercent(session) >= COMPLETION_PERCENT;
    }

    private List<AnalyticsArticleMetricDto> buildArticleMetrics(
            List<AnalyticsVisitEntity> visits,
            List<EncouragementEventEntity> encouragements
    ) {
        Map<String, AnalyticsContentIndexService.PostInfo> posts = contentIndexService.loadPostMap();
        Map<String, ArticleAccumulator> byPath = new LinkedHashMap<>();
        long articleVisitCount = visits.stream().filter(visit -> "article".equalsIgnoreCase(visit.getPageType())).count();
        for (AnalyticsVisitEntity visit : visits) {
            if (!"article".equalsIgnoreCase(visit.getPageType())) {
                continue;
            }
            String path = pathPolicy.normalizePath(visit.getPath());
            String title = resolveTitle(path, visit.getTitle(), posts);
            ArticleAccumulator accumulator = byPath.computeIfAbsent(path, key -> new ArticleAccumulator(path, title));
            accumulator.addVisit(visit, visitorKey(visit.getVisitorId(), visit.getIp()));
        }
        for (EncouragementEventEntity event : encouragements) {
            String path = pathPolicy.normalizePath(event.getPath());
            ArticleAccumulator accumulator = byPath.get(path);
            if (accumulator != null) {
                accumulator.encouragements += event.getDelta();
            }
        }
        long denominator = Math.max(1, articleVisitCount);
        Map<String, RecommendationMetricEntity> metrics = recommendationAggregationService.loadMetricsByUrls(
                new ArrayList<>(byPath.keySet())
        );
        return byPath.values().stream()
                .map(item -> toArticleMetric(item, denominator, metrics.get(item.path)))
                .sorted(Comparator.comparingLong(AnalyticsArticleMetricDto::visits).reversed())
                .toList();
    }

    private List<AnalyticsRecentVisitDto> buildRecentArticleVisits(List<AnalyticsVisitEntity> visits) {
        Map<String, AnalyticsContentIndexService.PostInfo> posts = contentIndexService.loadPostMap();
        Map<String, AnalyticsVisitorIdentityEntity> identities = identityMap();
        return visits.stream()
                .filter(visit -> "article".equalsIgnoreCase(visit.getPageType()))
                .sorted(Comparator.comparing(AnalyticsVisitEntity::getStartedAt).reversed())
                .limit(20)
                .map(visit -> {
                    String path = pathPolicy.normalizePath(visit.getPath());
                    AnalyticsVisitorIdentityEntity identity = identities.get(visit.getVisitorId());
                    return new AnalyticsRecentVisitDto(
                            path,
                            resolveTitle(path, visit.getTitle(), posts),
                            visit.getVisitorId(),
                            visitorIdentityService.displayName(identity),
                            ipRegionService.resolve(visit.getIp()),
                            Math.max(0, visit.getDurationMs()) / 1000L,
                            Math.max(0, Math.min(visit.getMaxScrollPercent(), 100)),
                            formatOffset(visit.getStartedAt())
                    );
                })
                .toList();
    }

    private List<AnalyticsTagMetricDto> buildTagMetrics(List<AnalyticsVisitEntity> visits) {
        Map<String, AnalyticsContentIndexService.PostInfo> posts = contentIndexService.loadPostMap();
        Map<String, TagAccumulator> byTag = new HashMap<>();
        for (AnalyticsVisitEntity visit : visits) {
            String path = pathPolicy.normalizePath(visit.getPath());
            AnalyticsContentIndexService.PostInfo post = posts.get(path);
            if (post == null) {
                continue;
            }
            for (String tag : post.tags()) {
                TagAccumulator accumulator = byTag.computeIfAbsent(tag, TagAccumulator::new);
                accumulator.visits++;
                accumulator.totalDurationMs += Math.max(0, visit.getDurationMs());
                accumulator.articlePaths.add(path);
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
        Map<String, AnalyticsVisitorIdentityEntity> identities = identityMap();
        for (EncouragementEventEntity event : events) {
            String key = visitorKey(event.getVisitorId(), event.getIp());
            VisitorAccumulator visitor = visitors.computeIfAbsent(
                    key,
                    ignored -> new VisitorAccumulator(event.getVisitorId(), event.getIp())
            );
            visitor.totalDelta += event.getDelta();
            visitor.settlements++;
            if (visitor.lastAt == null || event.getCreatedAt().isAfter(visitor.lastAt)) {
                visitor.lastAt = event.getCreatedAt();
            }
            String path = pathPolicy.normalizePath(event.getPath());
            ArticleAccumulator page = pages.computeIfAbsent(
                    path,
                    ignored -> new ArticleAccumulator(path, event.getTitle())
            );
            page.encouragements += event.getDelta();
        }
        return new EncouragementSummaryDto(
                encouragementRepository.sumAllDelta(),
                encouragementRepository.sumDeltaAfter(todayStart),
                encouragementRepository.sumDeltaAfter(weekStart),
                visitors.values().stream()
                        .map(item -> toVisitorMetric(item, identities.get(item.visitorId)))
                        .sorted(Comparator.comparingLong(EncouragementVisitorMetricDto::totalDelta).reversed())
                        .limit(20)
                        .toList(),
                pages.values().stream()
                        .map(item -> toArticleMetric(item, 1, null))
                        .sorted(Comparator.comparingLong(AnalyticsArticleMetricDto::encouragements).reversed())
                        .limit(8)
                        .toList(),
                encouragementRepository.findTop20ByOrderByCreatedAtDesc().stream()
                        .map(this::toEventDto)
                        .toList()
        );
    }

    private List<AnalyticsVisitorActivityDto> buildArticleVisitors(List<AnalyticsVisitEntity> visits) {
        Map<String, AnalyticsVisitorIdentityEntity> identities = identityMap();
        Map<String, VisitorActivityAccumulator> grouped = new LinkedHashMap<>();
        for (AnalyticsVisitEntity visit : visits) {
            String key = visitorKey(visit.getVisitorId(), visit.getIp());
            VisitorActivityAccumulator item = grouped.computeIfAbsent(
                    key,
                    ignored -> new VisitorActivityAccumulator(visit.getVisitorId(), visit.getIp())
            );
            item.visits++;
            item.totalDurationMs += Math.max(0, visit.getDurationMs());
            item.maxScrollPercent = Math.max(
                    item.maxScrollPercent,
                    Math.max(0, Math.min(visit.getMaxScrollPercent(), 100))
            );
            if (item.lastAt == null || visit.getStartedAt().isAfter(item.lastAt)) {
                item.lastAt = visit.getStartedAt();
            }
        }
        return grouped.values().stream()
                .map(item -> {
                    AnalyticsVisitorIdentityEntity identity = identities.get(item.visitorId);
                    return new AnalyticsVisitorActivityDto(
                            item.visitorId,
                            visitorIdentityService.displayName(identity),
                            identity == null ? "" : identity.getAvatar(),
                            item.ip,
                            ipRegionService.resolve(item.ip),
                            item.visits,
                            item.totalDurationMs / 1000L,
                            item.maxScrollPercent,
                            formatOffset(item.lastAt)
                    );
                })
                .sorted(Comparator.comparingLong(AnalyticsVisitorActivityDto::totalDurationSeconds).reversed())
                .toList();
    }

    private List<AnalyticsTrendPointDto> buildTrend(
            int days,
            OffsetDateTime since,
            List<AnalyticsVisitEntity> visits,
            List<EncouragementEventEntity> encouragements
    ) {
        Map<LocalDate, TrendAccumulator> trend = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            trend.put(since.toLocalDate().plusDays(i), new TrendAccumulator());
        }
        for (AnalyticsVisitEntity visit : visits) {
            TrendAccumulator item = trend.get(visit.getStartedAt().atZoneSameInstant(zoneId).toLocalDate());
            if (item != null) {
                item.visits++;
                item.visitors.add(visitorKey(visit.getVisitorId(), visit.getIp()));
            }
        }
        for (EncouragementEventEntity event : encouragements) {
            TrendAccumulator item = trend.get(event.getCreatedAt().atZoneSameInstant(zoneId).toLocalDate());
            if (item != null) {
                item.encouragements += event.getDelta();
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

    private <T> List<AnalyticsNamedMetricDto> namedDistribution(
            List<T> items,
            Function<T, String> nameResolver,
            boolean sumDuration
    ) {
        Map<String, long[]> grouped = new HashMap<>();
        for (T item : items) {
            String name = nameResolver.apply(item);
            String safeName = name == null || name.isBlank() ? "直接访问" : name;
            long[] values = grouped.computeIfAbsent(safeName, ignored -> new long[2]);
            values[0]++;
            if (sumDuration && item instanceof MusicListenSessionEntity music) {
                values[1] += music.getListenedMs() / 1000L;
            }
        }
        return grouped.entrySet().stream()
                .map(entry -> new AnalyticsNamedMetricDto(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingLong(AnalyticsNamedMetricDto::value).reversed())
                .limit(20)
                .toList();
    }

    private AnalyticsArticleMetricDto toArticleMetric(
            ArticleAccumulator item,
            long totalArticleVisits,
            RecommendationMetricEntity metric
    ) {
        long repeatVisitors = item.visitorVisits.values().stream().filter(count -> count > 1).count();
        int comments = metric == null ? 0 : Math.max(0, metric.getCommentCount());
        return new AnalyticsArticleMetricDto(
                item.path,
                item.title,
                item.visits,
                item.visitorVisits.size(),
                roundSeconds(item.totalDurationMs, item.visits),
                item.totalDurationMs / 1000L,
                percentage(item.visits, totalArticleVisits),
                percentage(repeatVisitors, item.visitorVisits.size()),
                averagePercent(item.totalScrollPercent, item.visits),
                percentage(item.completedVisits, item.visits),
                item.encouragements,
                comments
        );
    }

    private AnalyticsArticleMetricDto emptyArticleMetric(String path) {
        String title = resolveTitle(path, "", contentIndexService.loadPostMap());
        return new AnalyticsArticleMetricDto(
                path,
                title,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    private EncouragementVisitorMetricDto toVisitorMetric(
            VisitorAccumulator item,
            AnalyticsVisitorIdentityEntity identity
    ) {
        return new EncouragementVisitorMetricDto(
                item.visitorId,
                item.ip,
                visitorIdentityService.displayName(identity),
                identity == null ? "" : identity.getAvatar(),
                ipRegionService.resolve(item.ip),
                item.settlements,
                item.totalDelta,
                formatOffset(item.lastAt)
        );
    }

    private EncouragementEventDto toEventDto(EncouragementEventEntity event) {
        return new EncouragementEventDto(
                event.getVisitorId(),
                event.getIp(),
                event.getDelta(),
                pathPolicy.normalizePath(event.getPath()),
                event.getTitle(),
                formatOffset(event.getCreatedAt())
        );
    }

    private String resolveTitle(
            String path,
            String fallback,
            Map<String, AnalyticsContentIndexService.PostInfo> posts
    ) {
        AnalyticsContentIndexService.PostInfo post = posts.get(path);
        if (post != null && !post.title().isBlank()) {
            return post.title();
        }
        if (fallback != null && !fallback.isBlank() && !fallback.contains("%")) {
            return fallback;
        }
        int slash = path.lastIndexOf('/');
        String filename = slash >= 0 ? path.substring(slash + 1) : path;
        return filename.endsWith(".html") ? filename.substring(0, filename.length() - 5) : filename;
    }

    private Map<String, AnalyticsVisitorIdentityEntity> identityMap() {
        return identityRepository.findAll().stream().collect(Collectors.toMap(
                AnalyticsVisitorIdentityEntity::getVisitorId,
                Function.identity(),
                (left, right) -> right
        ));
    }

    private boolean matchesKeyword(AnalyticsArticleMetricDto item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return item.title().toLowerCase(Locale.ROOT).contains(value)
                || item.path().toLowerCase(Locale.ROOT).contains(value);
    }

    private Comparator<AnalyticsArticleMetricDto> articleComparator(String sort) {
        String value = sort == null ? "visits" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "duration" -> Comparator.comparingLong(AnalyticsArticleMetricDto::totalDurationSeconds).reversed();
            case "completion" -> Comparator.comparingDouble(AnalyticsArticleMetricDto::completionRate).reversed();
            case "scroll" -> Comparator.comparingDouble(AnalyticsArticleMetricDto::averageScrollPercent).reversed();
            default -> Comparator.comparingLong(AnalyticsArticleMetricDto::visits).reversed();
        };
    }

    private String visitorKey(String visitorId, String ip) {
        if (visitorId != null && !visitorId.isBlank() && !"anonymous".equalsIgnoreCase(visitorId)) {
            return visitorId;
        }
        return "ip:" + (ip == null ? "" : ip);
    }

    private String latestIp(
            List<AnalyticsVisitEntity> visits,
            List<EncouragementEventEntity> encouragements,
            List<MusicListenSessionEntity> listens
    ) {
        return java.util.stream.Stream.concat(
                        visits.stream().map(item -> new TimedIp(item.getStartedAt(), item.getIp())),
                        java.util.stream.Stream.concat(
                                encouragements.stream().map(item -> new TimedIp(item.getCreatedAt(), item.getIp())),
                                listens.stream().map(item -> new TimedIp(item.getUpdatedAt(), item.getIp()))
                        )
                )
                .filter(item -> item.time() != null)
                .max(Comparator.comparing(TimedIp::time))
                .map(TimedIp::ip)
                .orElse("");
    }

    private String deviceLabel(String userAgent) {
        String ua = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        String platform = ua.contains("android") ? "Android"
                : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("windows") ? "Windows"
                : ua.contains("mac os") ? "macOS"
                : ua.contains("linux") ? "Linux" : "未知系统";
        String browser = ua.contains("edg/") ? "Edge"
                : ua.contains("firefox/") ? "Firefox"
                : ua.contains("chrome/") ? "Chrome"
                : ua.contains("safari/") ? "Safari" : "未知浏览器";
        return platform + " · " + browser;
    }

    private String referrerLabel(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return "直接访问";
        }
        try {
            URI uri = URI.create(referrer);
            return uri.getHost() == null ? "站内跳转" : uri.getHost();
        } catch (IllegalArgumentException ignored) {
            return "其他来源";
        }
    }

    private long countUniqueVisitors(List<AnalyticsVisitEntity> visits) {
        return visits.stream().map(visit -> visitorKey(visit.getVisitorId(), visit.getIp())).distinct().count();
    }

    private double averageDurationSeconds(List<AnalyticsVisitEntity> visits) {
        return roundSeconds(visits.stream().mapToLong(AnalyticsVisitEntity::getDurationMs).sum(), visits.size());
    }

    private double roundSeconds(long durationMs, long count) {
        if (count <= 0) return 0;
        return Math.round(durationMs / 100.0 / count) / 10.0;
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) return 0;
        return Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    private double averagePercent(long totalPercent, long count) {
        if (count <= 0) return 0;
        return roundPercent((double) totalPercent / count);
    }

    private double roundPercent(double value) {
        return Math.round(Math.max(0, Math.min(value, 100)) * 10.0) / 10.0;
    }

    private int normalizeDays(int days) {
        return days <= 0 ? DEFAULT_DAYS : Math.min(days, MAX_DAYS);
    }

    private OffsetDateTime since(int days) {
        return OffsetDateTime.now(zoneId)
                .minusDays(days - 1L)
                .toLocalDate()
                .atStartOfDay(zoneId)
                .toOffsetDateTime();
    }

    private String formatOffset(OffsetDateTime value) {
        return value == null ? "" : value.atZoneSameInstant(zoneId).toOffsetDateTime().toString();
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
        private long totalScrollPercent;
        private long completedVisits;
        private long encouragements;
        private final Map<String, Long> visitorVisits = new HashMap<>();

        private ArticleAccumulator(String path, String title) {
            this.path = path;
            this.title = title == null || title.isBlank() ? path : title;
        }

        private void addVisit(AnalyticsVisitEntity visit, String visitorKey) {
            visits++;
            totalDurationMs += Math.max(0, visit.getDurationMs());
            int scrollPercent = Math.max(0, Math.min(visit.getMaxScrollPercent(), 100));
            totalScrollPercent += scrollPercent;
            if (scrollPercent >= COMPLETION_PERCENT) {
                completedVisits++;
            }
            visitorVisits.merge(visitorKey, 1L, Long::sum);
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
        private long settlements;
        private long totalDelta;
        private OffsetDateTime lastAt;

        private VisitorAccumulator(String visitorId, String ip) {
            this.visitorId = visitorId == null ? "" : visitorId;
            this.ip = ip == null ? "" : ip;
        }
    }

    private static final class VisitorActivityAccumulator {
        private final String visitorId;
        private final String ip;
        private long visits;
        private long totalDurationMs;
        private int maxScrollPercent;
        private OffsetDateTime lastAt;

        private VisitorActivityAccumulator(String visitorId, String ip) {
            this.visitorId = visitorId == null ? "" : visitorId;
            this.ip = ip == null ? "" : ip;
        }
    }

    private record TimedIp(OffsetDateTime time, String ip) {
    }
}
