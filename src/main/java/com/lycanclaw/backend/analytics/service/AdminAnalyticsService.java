package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.AdminAnalyticsSummaryDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticleDetailDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticleMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticlePageDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticleVisitDetailDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsDataQualityDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsNamedMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsPageMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsPagePageDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsRecentVisitDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsTagMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsTrendPointDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsVisitorActivityDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsVisitorProfileDto;
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
import com.lycanclaw.backend.content.service.ContentCatalogService;
import com.lycanclaw.backend.stats.entity.ArticleMetricEntity;
import com.lycanclaw.backend.stats.service.ArticleMetricService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
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
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_VISITOR_ID_LENGTH = 96;
    private static final int COMPLETION_PERCENT = 90;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter OFFSET_SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final AnalyticsVisitRepository visitRepository;
    private final EncouragementEventRepository encouragementRepository;
    private final AnalyticsVisitorIdentityRepository identityRepository;
    private final MusicListenSessionRepository musicRepository;
    private final ContentCatalogService contentCatalogService;
    private final AnalyticsPathPolicy pathPolicy;
    private final IpRegionService ipRegionService;
    private final VisitorIdentityService visitorIdentityService;
    private final ArticleMetricService articleMetricService;
    private final ZoneId zoneId;
    private final Set<String> excludedVisitorNames;
    private final Set<String> excludedWalineUserIds;

    public AdminAnalyticsService(
            AnalyticsVisitRepository visitRepository,
            EncouragementEventRepository encouragementRepository,
            AnalyticsVisitorIdentityRepository identityRepository,
            MusicListenSessionRepository musicRepository,
            ContentCatalogService contentCatalogService,
            AnalyticsPathPolicy pathPolicy,
            IpRegionService ipRegionService,
            VisitorIdentityService visitorIdentityService,
            ArticleMetricService articleMetricService,
            @Value("${lycan.system.zone-id:Asia/Shanghai}") String zoneId,
            @Value("${lycan.analytics.exclude.visitor-names:}") String excludedVisitorNames,
            @Value("${lycan.analytics.exclude.waline-user-ids:${lycan.security.admin-qq-whitelist:}}") String excludedWalineUserIds
    ) {
        this.visitRepository = visitRepository;
        this.encouragementRepository = encouragementRepository;
        this.identityRepository = identityRepository;
        this.musicRepository = musicRepository;
        this.contentCatalogService = contentCatalogService;
        this.pathPolicy = pathPolicy;
        this.ipRegionService = ipRegionService;
        this.visitorIdentityService = visitorIdentityService;
        this.articleMetricService = articleMetricService;
        this.zoneId = ZoneId.of(zoneId);
        this.excludedVisitorNames = parseSet(excludedVisitorNames);
        this.excludedWalineUserIds = parseSet(excludedWalineUserIds);
    }

    /**
     * 构建后台首页摘要，默认统计最近 30 天。
     */
    public AdminAnalyticsSummaryDto summary() {
        int safeDays = DEFAULT_DAYS;
        OffsetDateTime since = since(safeDays);
        List<AnalyticsVisitEntity> visits = filterExcludedVisits(visitRepository.findByStartedAtAfter(since));
        List<EncouragementEventEntity> encouragements = encouragementRepository.findByCreatedAtAfter(since);
        List<AnalyticsArticleMetricDto> articles = buildArticleMetrics(visits);
        return new AdminAnalyticsSummaryDto(
                safeDays,
                visits.size(),
                countUniqueVisitors(visits),
                averageDurationSeconds(visits),
                buildTrend(safeDays, since, visits, encouragements),
                articles.stream().limit(8).toList(),
                buildPageMetrics(visits).stream().limit(10).toList(),
                buildTagMetrics(visits).stream().limit(10).toList(),
                encouragementSummary(encouragements),
                dataQuality(safeDays, visits)
        );
    }

    /**
     * 按关键词、排序和分页条件返回普通页面访问统计。
     */
    public AnalyticsPagePageDto pageMetrics(
            int days,
            String keyword,
            String sort,
            int page,
            int pageSize
    ) {
        int safeDays = normalizeDays(days);
        List<AnalyticsVisitEntity> visits = filterExcludedVisits(visitRepository.findByStartedAtAfter(since(safeDays)));
        List<AnalyticsPageMetricDto> filtered = buildPageMetrics(visits).stream()
                .filter(item -> matchesPageKeyword(item, keyword))
                .sorted(pageComparator(sort))
                .toList();
        int safePageSize = normalizePageSize(pageSize);
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) safePageSize));
        int safePage = Math.min(normalizePage(page), totalPages);
        int from = Math.min(filtered.size(), (safePage - 1) * safePageSize);
        int to = Math.min(filtered.size(), from + safePageSize);
        return new AnalyticsPagePageDto(
                safePage,
                safePageSize,
                filtered.size(),
                totalPages,
                safeDays,
                filtered.subList(from, to),
                buildAbnormalPageMetrics(visits).stream().limit(10).toList()
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
        List<AnalyticsVisitEntity> visits = filterExcludedVisits(visitRepository.findByStartedAtAfter(since));
        List<AnalyticsArticleMetricDto> filtered = buildArticleMetrics(visits).stream()
                .filter(item -> matchesKeyword(item, keyword))
                .sorted(articleComparator(sort))
                .toList();
        int safePageSize = normalizePageSize(pageSize);
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) safePageSize));
        int safePage = Math.min(normalizePage(page), totalPages);
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
        if (!pathPolicy.isArticle(normalizedPath)) {
            throw new IllegalArgumentException("path 必须是文章路径");
        }
        OffsetDateTime since = since(safeDays);
        List<AnalyticsVisitEntity> visits = filterExcludedVisits(
                visitRepository.findByPathAndStartedAtAfter(normalizedPath, since)
        );
        AnalyticsArticleMetricDto metric = buildArticleMetrics(visits).stream()
                .findFirst()
                .orElseGet(() -> emptyArticleMetric(normalizedPath));
        return new AnalyticsArticleDetailDto(
                metric,
                buildTrend(safeDays, since, visits, List.of()),
                referrerDistribution(visits),
                buildArticleVisitors(visits),
                buildArticleRecentVisitDetails(visits)
        );
    }

    /**
     * 返回指定匿名访客的统一画像。
     */
    public AnalyticsVisitorProfileDto visitorProfile(int days, String visitorId) {
        int safeDays = normalizeDays(days);
        String normalizedVisitorId = normalizeVisitorId(visitorId);
        OffsetDateTime since = since(safeDays);
        List<AnalyticsVisitEntity> visits = visitRepository.findByVisitorIdAndStartedAtAfter(normalizedVisitorId, since);
        List<EncouragementEventEntity> encouragements =
                encouragementRepository.findByVisitorIdAndCreatedAtAfter(normalizedVisitorId, since);
        List<MusicListenSessionEntity> listens =
                musicRepository.findByVisitorIdAndStartedAtAfter(normalizedVisitorId, since);
        AnalyticsVisitorIdentityEntity identity = identityRepository.findByVisitorId(normalizedVisitorId).orElse(null);
        String ip = latestIp(visits, encouragements, listens);
        String device = visits.stream()
                .max(Comparator.comparing(AnalyticsVisitEntity::getStartedAt))
                .map(visit -> deviceLabel(visit.getUserAgent()))
                .orElse("");

        return new AnalyticsVisitorProfileDto(
                normalizedVisitorId,
                visitorIdentityService.displayName(normalizedVisitorId, identity),
                identity == null ? "" : identity.getAvatar(),
                identity == null ? "" : identity.getProvider(),
                ip,
                regionLabel(ip),
                device,
                visits.size(),
                visits.stream().mapToLong(AnalyticsVisitEntity::getDurationMs).sum() / 1000L,
                encouragements.stream().mapToLong(EncouragementEventEntity::getDelta).sum(),
                listens.stream().mapToLong(MusicListenSessionEntity::getListenedMs).sum() / 1000L,
                buildArticleMetrics(visits).stream().limit(8).toList(),
                referrerDistribution(visits),
                buildVisitorTopSongs(listens)
        );
    }

    /**
     * 返回音乐播放次数、听众、完成率、热门歌曲和最近收听记录。
     */
    public MusicAnalyticsSummaryDto musicAnalytics(int days) {
        int safeDays = normalizeDays(days);
        List<MusicListenSessionEntity> sessions = musicRepository.findByStartedAtAfter(since(safeDays));
        long completed = sessions.stream().filter(this::isMusicCompleted).count();
        List<MusicListenSessionEntity> recentSessions = sessions.stream()
                .sorted(Comparator.comparing(MusicListenSessionEntity::getUpdatedAt).reversed())
                .limit(20)
                .toList();
        // 最近收听只展示 20 条，身份查询也限制在展示范围内。
        Map<String, AnalyticsVisitorIdentityEntity> identities = identityMap(
                recentSessions.stream().map(MusicListenSessionEntity::getVisitorId).toList()
        );
        List<MusicListenRecordDto> recent = recentSessions.stream()
                .map(item -> {
                    AnalyticsVisitorIdentityEntity identity = identities.get(item.getVisitorId());
                    return new MusicListenRecordDto(
                            item.getVisitorId(),
                            visitorIdentityService.displayName(item.getVisitorId(), identity),
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

    private List<AnalyticsArticleMetricDto> buildArticleMetrics(List<AnalyticsVisitEntity> visits) {
        Map<String, ContentCatalogService.ContentItem> posts = contentCatalogService.loadArticleMap();
        Map<String, ArticleAccumulator> byPath = new LinkedHashMap<>();
        for (AnalyticsVisitEntity visit : visits) {
            if (!"article".equalsIgnoreCase(visit.getPageType())) {
                continue;
            }
            String path = pathPolicy.normalizePath(visit.getPath());
            String title = resolveTitle(path, visit.getTitle(), posts);
            ArticleAccumulator accumulator = byPath.computeIfAbsent(path, key -> new ArticleAccumulator(path, title));
            accumulator.addVisit(visit, visitorKey(visit.getVisitorId(), visit.getIp()));
        }
        Map<String, ArticleMetricEntity> metrics = articleMetricService.loadEntities(
                List.copyOf(byPath.keySet())
        );
        return byPath.values().stream()
                .map(item -> toArticleMetric(item, metrics.get(item.path)))
                .sorted(Comparator.comparingLong(AnalyticsArticleMetricDto::visits).reversed())
                .toList();
    }

    private List<AnalyticsPageMetricDto> buildPageMetrics(List<AnalyticsVisitEntity> visits) {
        Map<String, PageAccumulator> byPath = new LinkedHashMap<>();
        for (AnalyticsVisitEntity visit : visits) {
            if ("article".equalsIgnoreCase(visit.getPageType()) || isAbnormalPageVisit(visit)) {
                continue;
            }
            String path = canonicalPagePath(visit.getPath());
            PageAccumulator accumulator = byPath.computeIfAbsent(
                    path,
                    key -> new PageAccumulator(path, resolveTitle(path, visit.getTitle(), Map.of()))
            );
            accumulator.addVisit(visit, visitorKey(visit.getVisitorId(), visit.getIp()));
        }
        return byPath.values().stream()
                .map(this::toPageMetric)
                .sorted(Comparator.comparingLong(AnalyticsPageMetricDto::visits).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                AnalyticsPageMetricDto::averageDurationSeconds
                        ).reversed())
                        .thenComparing(AnalyticsPageMetricDto::path))
                .toList();
    }

    private List<AnalyticsPageMetricDto> buildAbnormalPageMetrics(List<AnalyticsVisitEntity> visits) {
        Map<String, PageAccumulator> byPath = new LinkedHashMap<>();
        for (AnalyticsVisitEntity visit : visits) {
            if ("article".equalsIgnoreCase(visit.getPageType()) || !isAbnormalPageVisit(visit)) {
                continue;
            }
            String path = canonicalPagePath(visit.getPath());
            PageAccumulator accumulator = byPath.computeIfAbsent(
                    path,
                    key -> new PageAccumulator(path, resolveTitle(path, visit.getTitle(), Map.of()))
            );
            accumulator.addVisit(visit, visitorKey(visit.getVisitorId(), visit.getIp()));
        }
        return byPath.values().stream()
                .map(this::toPageMetric)
                .sorted(Comparator.comparingLong(AnalyticsPageMetricDto::visits).reversed()
                        .thenComparing(AnalyticsPageMetricDto::path))
                .toList();
    }

    private List<AnalyticsRecentVisitDto> buildRecentArticleVisits(List<AnalyticsVisitEntity> visits) {
        Map<String, ContentCatalogService.ContentItem> posts = contentCatalogService.loadArticleMap();
        List<AnalyticsVisitEntity> recentVisits = visits.stream()
                .filter(visit -> "article".equalsIgnoreCase(visit.getPageType()))
                .sorted(Comparator.comparing(AnalyticsVisitEntity::getStartedAt).reversed())
                .limit(20)
                .toList();
        // 最近访问只展示 20 条，身份查询也限制在展示范围内。
        Map<String, AnalyticsVisitorIdentityEntity> identities = identityMap(
                recentVisits.stream().map(AnalyticsVisitEntity::getVisitorId).toList()
        );
        return recentVisits.stream()
                .map(visit -> {
                    String path = pathPolicy.normalizePath(visit.getPath());
                    AnalyticsVisitorIdentityEntity identity = identities.get(visit.getVisitorId());
                    return new AnalyticsRecentVisitDto(
                            path,
                            resolveTitle(path, visit.getTitle(), posts),
                            visit.getVisitorId(),
                            visitorIdentityService.displayName(visit.getVisitorId(), identity),
                            regionLabel(visit.getIp()),
                            Math.max(0, visit.getDurationMs()) / 1000L,
                            Math.max(0, Math.min(visit.getMaxScrollPercent(), 100)),
                            formatOffset(visit.getStartedAt())
                    );
                })
                .toList();
    }

    private List<AnalyticsTagMetricDto> buildTagMetrics(List<AnalyticsVisitEntity> visits) {
        Map<String, ContentCatalogService.ContentItem> posts = contentCatalogService.loadArticleMap();
        Map<String, TagAccumulator> byTag = new HashMap<>();
        for (AnalyticsVisitEntity visit : visits) {
            String path = pathPolicy.normalizePath(visit.getPath());
            ContentCatalogService.ContentItem post = posts.get(path);
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

    private List<AnalyticsNamedMetricDto> buildVisitorTopSongs(List<MusicListenSessionEntity> listens) {
        Map<String, List<MusicListenSessionEntity>> grouped = listens.stream()
                .collect(Collectors.groupingBy(
                        MusicListenSessionEntity::getSongId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return grouped.values().stream()
                .map(items -> {
                    MusicListenSessionEntity sample = items.get(0);
                    String name = sample.getArtist() == null || sample.getArtist().isBlank()
                            ? sample.getSongName()
                            : sample.getSongName() + " · " + sample.getArtist();
                    long listenedSeconds = items.stream()
                            .mapToLong(MusicListenSessionEntity::getListenedMs)
                            .sum() / 1000L;
                    return new AnalyticsNamedMetricDto(name, items.size(), listenedSeconds);
                })
                .sorted(Comparator.comparingLong(AnalyticsNamedMetricDto::value).reversed())
                .limit(20)
                .toList();
    }

    private EncouragementSummaryDto encouragementSummary(List<EncouragementEventEntity> events) {
        OffsetDateTime todayStart = OffsetDateTime.now(zoneId).toLocalDate().atStartOfDay(zoneId).toOffsetDateTime();
        Map<String, VisitorAccumulator> visitors = new LinkedHashMap<>();
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
        }
        List<VisitorAccumulator> topVisitors = visitors.values().stream()
                .sorted(Comparator.comparingLong((VisitorAccumulator item) -> item.totalDelta).reversed())
                .limit(20)
                .toList();
        // 催更榜只展示前 20 名，身份查询也限制在展示范围内。
        Map<String, AnalyticsVisitorIdentityEntity> identities = identityMap(
                topVisitors.stream().map(item -> item.visitorId).toList()
        );
        return new EncouragementSummaryDto(
                encouragementRepository.sumAllDelta(),
                encouragementRepository.sumDeltaAfter(todayStart),
                topVisitors.stream()
                        .map(item -> toVisitorMetric(item, identities.get(item.visitorId)))
                        .toList()
        );
    }

    private List<AnalyticsVisitorActivityDto> buildArticleVisitors(List<AnalyticsVisitEntity> visits) {
        Map<String, AnalyticsVisitorIdentityEntity> identities = identityMap(
                visits.stream().map(AnalyticsVisitEntity::getVisitorId).toList()
        );
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
                            visitorIdentityService.displayName(item.visitorId, identity),
                            identity == null ? "" : identity.getAvatar(),
                            item.ip,
                            regionLabel(item.ip),
                            item.visits,
                            item.totalDurationMs / 1000L,
                            item.maxScrollPercent,
                            formatOffset(item.lastAt)
                    );
                })
                .sorted(Comparator.comparingLong(AnalyticsVisitorActivityDto::totalDurationSeconds).reversed())
                .toList();
    }

    private List<AnalyticsArticleVisitDetailDto> buildArticleRecentVisitDetails(List<AnalyticsVisitEntity> visits) {
        List<AnalyticsVisitEntity> recentVisits = visits.stream()
                .sorted(Comparator.comparing(AnalyticsVisitEntity::getStartedAt).reversed())
                .limit(50)
                .toList();
        Map<String, AnalyticsVisitorIdentityEntity> identities = identityMap(
                recentVisits.stream().map(AnalyticsVisitEntity::getVisitorId).toList()
        );
        return recentVisits.stream()
                .map(visit -> {
                    AnalyticsVisitorIdentityEntity identity = identities.get(visit.getVisitorId());
                    return new AnalyticsArticleVisitDetailDto(
                            visit.getVisitorId(),
                            visitorIdentityService.displayName(visit.getVisitorId(), identity),
                            identity == null ? "" : identity.getAvatar(),
                            regionLabel(visit.getIp()),
                            referrerLabel(visit.getReferrer()),
                            Math.max(0, visit.getDurationMs()) / 1000L,
                            Math.max(0, Math.min(visit.getMaxScrollPercent(), 100)),
                            formatOffset(visit.getStartedAt())
                    );
                })
                .toList();
    }

    private AnalyticsDataQualityDto dataQuality(int days, List<AnalyticsVisitEntity> visits) {
        return new AnalyticsDataQualityDto(
                days,
                visits.size(),
                countUniqueVisitors(visits),
                visits.stream().filter(this::isNotFoundVisit).count(),
                visits.stream().filter(this::isAbnormalPageVisit).count(),
                ipRegionService.isAvailable()
        );
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

    private List<AnalyticsNamedMetricDto> referrerDistribution(List<AnalyticsVisitEntity> visits) {
        Map<String, long[]> grouped = new HashMap<>();
        for (AnalyticsVisitEntity visit : visits) {
            String name = referrerLabel(visit.getReferrer());
            String safeName = name == null || name.isBlank() ? "直接访问" : name;
            long[] values = grouped.computeIfAbsent(safeName, ignored -> new long[2]);
            values[0]++;
        }
        return grouped.entrySet().stream()
                .map(entry -> new AnalyticsNamedMetricDto(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingLong(AnalyticsNamedMetricDto::value).reversed())
                .limit(20)
                .toList();
    }

    private AnalyticsArticleMetricDto toArticleMetric(
            ArticleAccumulator item,
            ArticleMetricEntity metric
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
                percentage(repeatVisitors, item.visitorVisits.size()),
                averagePercent(item.totalScrollPercent, item.visits),
                percentage(item.completedVisits, item.visits),
                comments
        );
    }

    private AnalyticsPageMetricDto toPageMetric(PageAccumulator item) {
        return new AnalyticsPageMetricDto(
                item.path,
                item.title,
                item.visits,
                item.visitors.size(),
                roundSeconds(item.totalDurationMs, item.visits),
                formatOffset(item.lastAt)
        );
    }

    private AnalyticsArticleMetricDto emptyArticleMetric(String path) {
        String title = resolveTitle(path, "", contentCatalogService.loadArticleMap());
        ArticleMetricEntity metric = articleMetricService.loadEntities(List.of(path)).get(path);
        int comments = metric == null ? 0 : Math.max(0, metric.getCommentCount());
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
                comments
        );
    }

    private EncouragementVisitorMetricDto toVisitorMetric(
            VisitorAccumulator item,
            AnalyticsVisitorIdentityEntity identity
    ) {
        return new EncouragementVisitorMetricDto(
                item.visitorId,
                item.ip,
                visitorIdentityService.displayName(item.visitorId, identity),
                identity == null ? "" : identity.getAvatar(),
                regionLabel(item.ip),
                item.settlements,
                item.totalDelta,
                formatOffset(item.lastAt)
        );
    }

    private String resolveTitle(
            String path,
            String fallback,
            Map<String, ContentCatalogService.ContentItem> posts
    ) {
        ContentCatalogService.ContentItem post = posts.get(path);
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

    private Map<String, AnalyticsVisitorIdentityEntity> identityMap(Collection<String> visitorIds) {
        List<String> normalizedIds = visitorIds.stream()
                .filter(value -> value != null && !value.isBlank() && !"anonymous".equalsIgnoreCase(value))
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        return identityRepository.findByVisitorIdIn(normalizedIds).stream().collect(Collectors.toMap(
                AnalyticsVisitorIdentityEntity::getVisitorId,
                Function.identity(),
                (left, right) -> right
        ));
    }

    private List<AnalyticsVisitEntity> filterExcludedVisits(List<AnalyticsVisitEntity> visits) {
        if (visits.isEmpty() || (excludedVisitorNames.isEmpty() && excludedWalineUserIds.isEmpty())) {
            return visits;
        }
        Map<String, AnalyticsVisitorIdentityEntity> identities = identityMap(
                visits.stream().map(AnalyticsVisitEntity::getVisitorId).toList()
        );
        return visits.stream()
                .filter(visit -> !isExcludedVisitor(identities.get(visit.getVisitorId())))
                .toList();
    }

    private boolean isExcludedVisitor(AnalyticsVisitorIdentityEntity identity) {
        if (identity == null) {
            return false;
        }
        return excludedVisitorNames.contains(identity.getNickname())
                || excludedWalineUserIds.contains(identity.getWalineUserId());
    }

    private Set<String> parseSet(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Set.of();
        }
        Set<String> values = new HashSet<>();
        for (String item : rawValue.split(",")) {
            String value = item == null ? "" : item.trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return Set.copyOf(values);
    }

    private boolean matchesKeyword(AnalyticsArticleMetricDto item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return item.title().toLowerCase(Locale.ROOT).contains(value)
                || item.path().toLowerCase(Locale.ROOT).contains(value);
    }

    private boolean matchesPageKeyword(AnalyticsPageMetricDto item, String keyword) {
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
            case "visits", "" -> Comparator.comparingLong(AnalyticsArticleMetricDto::visits).reversed();
            case "duration" -> Comparator.comparingLong(AnalyticsArticleMetricDto::totalDurationSeconds).reversed();
            case "completion" -> Comparator.comparingDouble(AnalyticsArticleMetricDto::completionRate).reversed();
            case "scroll" -> Comparator.comparingDouble(AnalyticsArticleMetricDto::averageScrollPercent).reversed();
            default -> throw new IllegalArgumentException("sort 仅支持 visits、duration、completion、scroll");
        };
    }

    private Comparator<AnalyticsPageMetricDto> pageComparator(String sort) {
        String value = sort == null ? "visits" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "visits", "" -> Comparator.comparingLong(AnalyticsPageMetricDto::visits).reversed();
            case "visitors" -> Comparator.comparingLong(AnalyticsPageMetricDto::uniqueVisitors).reversed();
            case "duration" -> Comparator.comparingDouble(AnalyticsPageMetricDto::averageDurationSeconds).reversed();
            case "recent" -> Comparator.comparing(AnalyticsPageMetricDto::lastVisitedAt).reversed();
            default -> throw new IllegalArgumentException("sort 仅支持 visits、visitors、duration、recent");
        };
    }

    private String canonicalPagePath(String path) {
        String normalized = pathPolicy.normalizePath(path);
        if ("/index.html".equals(normalized)) {
            return "/";
        }
        if (normalized.endsWith("/index.html")) {
            String canonical = normalized.substring(0, normalized.length() - "index.html".length());
            return canonical.isBlank() ? "/" : canonical;
        }
        return normalized;
    }

    private boolean isAbnormalPageVisit(AnalyticsVisitEntity visit) {
        if ("article".equalsIgnoreCase(visit.getPageType())) {
            return false;
        }
        String path = canonicalPagePath(visit.getPath());
        return isNotFoundVisit(visit) || path.contains("%");
    }

    private boolean isNotFoundVisit(AnalyticsVisitEntity visit) {
        String title = visit.getTitle() == null ? "" : visit.getTitle().trim();
        String path = canonicalPagePath(visit.getPath()).toLowerCase(Locale.ROOT);
        return "404".equals(title) || "/404.html".equals(path) || path.endsWith("/404.html");
    }

    private String regionLabel(String ip) {
        String region = ipRegionService.resolve(ip);
        return region == null || region.isBlank() ? "未知地区" : region;
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
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("days 必须在 1 到 365 之间");
        }
        return days;
    }

    private int normalizePage(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("page 必须大于等于 1");
        }
        return page;
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize 必须在 1 到 50 之间");
        }
        return pageSize;
    }

    private String normalizeVisitorId(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            throw new IllegalArgumentException("visitorId 不能为空");
        }
        String normalized = visitorId.trim();
        if (normalized.length() > MAX_VISITOR_ID_LENGTH) {
            throw new IllegalArgumentException("visitorId 长度不能超过 96");
        }
        return normalized;
    }

    private OffsetDateTime since(int days) {
        return OffsetDateTime.now(zoneId)
                .minusDays(days - 1L)
                .toLocalDate()
                .atStartOfDay(zoneId)
                .toOffsetDateTime();
    }

    private String formatOffset(OffsetDateTime value) {
        return value == null
                ? ""
                : value.atZoneSameInstant(zoneId).truncatedTo(ChronoUnit.SECONDS).toOffsetDateTime().format(OFFSET_SECOND_FORMATTER);
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

    private static final class PageAccumulator {
        private final String path;
        private final String title;
        private long visits;
        private long totalDurationMs;
        private final Set<String> visitors = new HashSet<>();
        private OffsetDateTime lastAt;

        private PageAccumulator(String path, String title) {
            this.path = path;
            this.title = title;
        }

        private void addVisit(AnalyticsVisitEntity visit, String visitorKey) {
            visits++;
            totalDurationMs += Math.max(0, visit.getDurationMs());
            visitors.add(visitorKey);
            if (lastAt == null || visit.getStartedAt().isAfter(lastAt)) {
                lastAt = visit.getStartedAt();
            }
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
