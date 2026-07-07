package com.lycanclaw.backend.analytics.service;

import com.lycanclaw.backend.analytics.dto.AdminAnalyticsSummaryDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticleDetailDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsArticlePageDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsNamedMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsPageMetricDto;
import com.lycanclaw.backend.analytics.dto.AnalyticsVisitorProfileDto;
import com.lycanclaw.backend.analytics.dto.MusicAnalyticsSummaryDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端统计聚合服务测试。
 * 验证文章指标、定向详情查询、访客画像和音乐排行语义。
 *
 * @author Wreckloud
 * @since 2026-06-24
 */
class AdminAnalyticsServiceTest {

    private final AnalyticsVisitRepository visitRepository = mock(AnalyticsVisitRepository.class);
    private final EncouragementEventRepository encouragementRepository = mock(EncouragementEventRepository.class);
    private final AnalyticsVisitorIdentityRepository identityRepository =
            mock(AnalyticsVisitorIdentityRepository.class);
    private final MusicListenSessionRepository musicRepository = mock(MusicListenSessionRepository.class);
    private final ContentCatalogService contentCatalogService = mock(ContentCatalogService.class);
    private final IpRegionService ipRegionService = mock(IpRegionService.class);
    private final VisitorIdentityService visitorIdentityService = mock(VisitorIdentityService.class);
    private final ArticleMetricService articleMetricService = mock(ArticleMetricService.class);

    private AdminAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = createService("", "");
        when(contentCatalogService.loadArticleMap()).thenReturn(Map.of());
        when(articleMetricService.loadEntities(anyList())).thenReturn(Map.of());
        when(identityRepository.findByVisitorIdIn(anyCollection())).thenReturn(List.of());
    }

    @Test
    void aggregatesArticleMetricsWithSnapshotComments() {
        String path = "/thoughts/example.html";
        AnalyticsVisitEntity first = visit(path, "visitor-1", 10_000, 50, 1);
        AnalyticsVisitEntity second = visit(path, "visitor-1", 20_000, 100, 2);
        ArticleMetricEntity metric = new ArticleMetricEntity();
        metric.setPath(path);
        metric.setCommentCount(3);
        when(visitRepository.findByStartedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(List.of(first, second));
        when(contentCatalogService.loadArticleMap()).thenReturn(Map.of(
                path,
                new ContentCatalogService.ContentItem(
                        path,
                        "示例文章",
                        "",
                        "2026-06-24 12:00:00",
                        List.of("Java"),
                        ContentCatalogService.ContentKind.THOUGHT
                )
        ));
        when(articleMetricService.loadEntities(List.of(path))).thenReturn(Map.of(path, metric));

        AnalyticsArticlePageDto result = service.articleMetrics(30, "", "visits", 1, 12);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).visits()).isEqualTo(2);
        assertThat(result.items().get(0).uniqueVisitors()).isEqualTo(1);
        assertThat(result.items().get(0).averageDurationSeconds()).isEqualTo(15.0);
        assertThat(result.items().get(0).revisitRate()).isEqualTo(100.0);
        assertThat(result.items().get(0).averageScrollPercent()).isEqualTo(75.0);
        assertThat(result.items().get(0).completionRate()).isEqualTo(50.0);
        assertThat(result.items().get(0).commentCount()).isEqualTo(3);
    }

    @Test
    void aggregatesNonArticlePagesForOverview() {
        AnalyticsVisitEntity home = visit("/", "visitor-1", 5_000, 0, 1);
        home.setTitle("首页");
        AnalyticsVisitEntity about = visit("/about.html", "visitor-2", 10_000, 0, 2);
        about.setTitle("关于");
        AnalyticsVisitEntity article = visit("/thoughts/example.html", "visitor-3", 20_000, 80, 3);
        when(visitRepository.findByStartedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(List.of(home, about, article));
        when(encouragementRepository.findByCreatedAtAfter(any(OffsetDateTime.class))).thenReturn(List.of());

        AdminAnalyticsSummaryDto result = service.summary();

        assertThat(result.topPages()).extracting(AnalyticsPageMetricDto::path)
                .containsExactly("/about.html", "/");
        assertThat(result.topPages()).extracting(AnalyticsPageMetricDto::title)
                .containsExactly("关于", "首页");
        assertThat(result.topPages()).extracting(AnalyticsPageMetricDto::averageDurationSeconds)
                .containsExactly(10.0, 5.0);
    }

    @Test
    void mergesHomeIndexPathForPageMetrics() {
        AnalyticsVisitEntity home = visit("/", "visitor-1", 5_000, 0, 1);
        home.setTitle("LycanClaw");
        AnalyticsVisitEntity index = visit("/index.html", "visitor-2", 15_000, 0, 2);
        index.setTitle("LycanClaw");
        when(visitRepository.findByStartedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(List.of(home, index));

        var result = service.pageMetrics(30, "", "visits", 1, 12);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).path()).isEqualTo("/");
        assertThat(result.items().get(0).visits()).isEqualTo(2);
        assertThat(result.items().get(0).uniqueVisitors()).isEqualTo(2);
    }

    @Test
    void keepsSameTitlePagesSeparatedByPath() {
        AnalyticsVisitEntity first = visit("/about.html", "visitor-1", 5_000, 0, 1);
        first.setTitle("LycanClaw");
        AnalyticsVisitEntity second = visit("/projects.html", "visitor-2", 5_000, 0, 2);
        second.setTitle("LycanClaw");
        when(visitRepository.findByStartedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(List.of(first, second));

        var result = service.pageMetrics(30, "", "visits", 1, 12);

        assertThat(result.items()).extracting(AnalyticsPageMetricDto::path)
                .containsExactly("/about.html", "/projects.html");
        assertThat(result.items()).extracting(AnalyticsPageMetricDto::title)
                .containsExactly("LycanClaw", "LycanClaw");
    }

    @Test
    void keepsNotFoundVisitsOutOfNormalPageMetrics() {
        AnalyticsVisitEntity normal = visit("/about.html", "visitor-1", 5_000, 0, 1);
        normal.setTitle("关于");
        AnalyticsVisitEntity notFound = visit("/missing.html", "visitor-2", 0, 0, 2);
        notFound.setTitle("404");
        when(visitRepository.findByStartedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(List.of(normal, notFound));
        when(encouragementRepository.findByCreatedAtAfter(any(OffsetDateTime.class))).thenReturn(List.of());

        var result = service.pageMetrics(30, "", "visits", 1, 12);
        AdminAnalyticsSummaryDto summary = service.summary();

        assertThat(result.items()).extracting(AnalyticsPageMetricDto::path)
                .containsExactly("/about.html");
        assertThat(result.abnormalPages()).extracting(AnalyticsPageMetricDto::path)
                .containsExactly("/missing.html");
        assertThat(summary.dataQuality().notFoundVisits()).isEqualTo(1);
    }

    @Test
    void excludesConfiguredOwnerVisitsFromAdminOverview() {
        AnalyticsVisitEntity owner = visit("/about.html", "owner-visitor", 10_000, 0, 1);
        owner.setTitle("关于");
        AnalyticsVisitEntity guest = visit("/about.html", "guest-visitor", 20_000, 0, 2);
        guest.setTitle("关于");
        AnalyticsVisitorIdentityEntity ownerIdentity = new AnalyticsVisitorIdentityEntity();
        ownerIdentity.setVisitorId("owner-visitor");
        ownerIdentity.setNickname("维克罗德");
        when(visitRepository.findByStartedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(List.of(owner, guest));
        when(encouragementRepository.findByCreatedAtAfter(any(OffsetDateTime.class))).thenReturn(List.of());
        when(identityRepository.findByVisitorIdIn(anyCollection())).thenReturn(List.of(ownerIdentity));

        AdminAnalyticsSummaryDto result = createService("维克罗德", "").summary();

        assertThat(result.visits()).isEqualTo(1);
        assertThat(result.uniqueVisitors()).isEqualTo(1);
        assertThat(result.topPages()).hasSize(1);
        assertThat(result.topPages().get(0).visits()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownArticleSort() {
        when(visitRepository.findByStartedAtAfter(any(OffsetDateTime.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.articleMetrics(30, "", "unknown", 1, 12))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sort 仅支持");
    }

    @Test
    void rejectsInvalidArticleQueryParams() {
        when(visitRepository.findByStartedAtAfter(any(OffsetDateTime.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.articleMetrics(0, "", "visits", 1, 12))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("days 必须在 1 到 365 之间");
        assertThatThrownBy(() -> service.articleMetrics(366, "", "visits", 1, 12))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("days 必须在 1 到 365 之间");
        assertThatThrownBy(() -> service.articleMetrics(30, "", "visits", 0, 12))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page 必须大于等于 1");
        assertThatThrownBy(() -> service.articleMetrics(30, "", "visits", 1, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize 必须在 1 到 50 之间");
    }

    @Test
    void loadsArticleDetailByNormalizedPath() {
        String path = "/thoughts/example.html";
        when(visitRepository.findByPathAndStartedAtAfter(eq(path), any(OffsetDateTime.class)))
                .thenReturn(List.of(visit(path, "visitor-1", 8_000, 80, 1)));

        service.articleDetail(30, path + "?from=admin");

        verify(visitRepository).findByPathAndStartedAtAfter(eq(path), any(OffsetDateTime.class));
        verify(visitRepository, never()).findByStartedAtAfter(any(OffsetDateTime.class));
    }

    @Test
    void rejectsNonArticleDetailPath() {
        assertThatThrownBy(() -> service.articleDetail(30, "/about.html"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path 必须是文章路径");

        verify(visitRepository, never()).findByPathAndStartedAtAfter(any(), any(OffsetDateTime.class));
    }

    @Test
    void keepsSnapshotCommentsWhenArticleHasNoRecentVisits() {
        String path = "/thoughts/no-recent-visit.html";
        ArticleMetricEntity metric = new ArticleMetricEntity();
        metric.setPath(path);
        metric.setCommentCount(7);
        when(visitRepository.findByPathAndStartedAtAfter(eq(path), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(contentCatalogService.loadArticleMap()).thenReturn(Map.of(
                path,
                new ContentCatalogService.ContentItem(
                        path,
                        "无访问文章",
                        "",
                        "2026-06-24 12:00:00",
                        List.of(),
                        ContentCatalogService.ContentKind.THOUGHT
                )
        ));
        when(articleMetricService.loadEntities(List.of(path))).thenReturn(Map.of(path, metric));

        AnalyticsArticleDetailDto result = service.articleDetail(30, path);

        assertThat(result.metric().visits()).isZero();
        assertThat(result.metric().commentCount()).isEqualTo(7);
        assertThat(result.metric().title()).isEqualTo("无访问文章");
    }

    @Test
    void articleDetailIncludesSecondLevelRecentVisitRecords() {
        String path = "/thoughts/detail.html";
        AnalyticsVisitEntity visit = visit(path, "visitor-1", 8_000, 88, 1);
        when(visitRepository.findByPathAndStartedAtAfter(eq(path), any(OffsetDateTime.class)))
                .thenReturn(List.of(visit));
        when(visitorIdentityService.displayName("visitor-1", null)).thenReturn("匿名-TEST0001");
        when(ipRegionService.resolve("203.0.113.1")).thenReturn("四川省 成都市");

        AnalyticsArticleDetailDto result = service.articleDetail(30, path);

        assertThat(result.recentVisits()).hasSize(1);
        assertThat(result.recentVisits().get(0).nickname()).isEqualTo("匿名-TEST0001");
        assertThat(result.recentVisits().get(0).region()).isEqualTo("四川省 成都市");
        assertThat(result.recentVisits().get(0).durationSeconds()).isEqualTo(8);
        assertThat(result.recentVisits().get(0).maxScrollPercent()).isEqualTo(88);
        assertThat(result.recentVisits().get(0).visitedAt()).isEqualTo("2026-06-24T12:01:00+08:00");
    }

    @Test
    void loadsVisitorProfileFromVisitorScopedQueries() {
        String visitorId = "visitor-1";
        when(visitRepository.findByVisitorIdAndStartedAtAfter(eq(visitorId), any(OffsetDateTime.class)))
                .thenReturn(List.of(visit("/about.html", visitorId, 6_000, 0, 1)));
        when(encouragementRepository.findByVisitorIdAndCreatedAtAfter(eq(visitorId), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(musicRepository.findByVisitorIdAndStartedAtAfter(eq(visitorId), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(visitorIdentityService.displayName(visitorId, null)).thenReturn("匿名-TEST0001");

        AnalyticsVisitorProfileDto result = service.visitorProfile(365, visitorId);

        assertThat(result.visitorId()).isEqualTo(visitorId);
        assertThat(result.nickname()).isEqualTo("匿名-TEST0001");
        assertThat(result.visits()).isEqualTo(1);
        verify(visitRepository, never()).findByStartedAtAfter(any(OffsetDateTime.class));
        verify(encouragementRepository, never()).findByCreatedAtAfter(any(OffsetDateTime.class));
        verify(musicRepository, never()).findByStartedAtAfter(any(OffsetDateTime.class));
    }

    @Test
    void rejectsInvalidVisitorProfileParams() {
        assertThatThrownBy(() -> service.visitorProfile(30, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("visitorId 不能为空");
        assertThatThrownBy(() -> service.visitorProfile(30, "v".repeat(97)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("visitorId 长度不能超过 96");
    }

    @Test
    void keepsSameNameSongsSeparateBySongId() {
        String visitorId = "visitor-1";
        MusicListenSessionEntity first = listen("session-1", "song-a", "同名歌曲", visitorId, 1);
        first.setArtist("歌手甲");
        MusicListenSessionEntity second = listen("session-2", "song-b", "同名歌曲", visitorId, 2);
        second.setArtist("歌手乙");
        when(visitRepository.findByVisitorIdAndStartedAtAfter(eq(visitorId), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(encouragementRepository.findByVisitorIdAndCreatedAtAfter(eq(visitorId), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(musicRepository.findByVisitorIdAndStartedAtAfter(eq(visitorId), any(OffsetDateTime.class)))
                .thenReturn(List.of(first, second));
        when(visitorIdentityService.displayName(visitorId, null)).thenReturn("匿名-TEST0001");

        AnalyticsVisitorProfileDto result = service.visitorProfile(30, visitorId);

        assertThat(result.topSongs()).extracting(AnalyticsNamedMetricDto::name)
                .containsExactly("同名歌曲 · 歌手甲", "同名歌曲 · 歌手乙");
    }

    @Test
    void ranksPopularSongsByPlayCount() {
        MusicListenSessionEntity first = listen("session-1", "song-a", "歌曲A", "visitor-1", 1);
        MusicListenSessionEntity second = listen("session-2", "song-a", "歌曲A", "visitor-2", 2);
        MusicListenSessionEntity third = listen("session-3", "song-b", "歌曲B", "visitor-1", 3);
        when(musicRepository.findByStartedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(List.of(first, second, third));

        MusicAnalyticsSummaryDto result = service.musicAnalytics(30);

        assertThat(result.topSongs()).hasSize(2);
        assertThat(result.topSongs().get(0).songId()).isEqualTo("song-a");
        assertThat(result.topSongs().get(0).plays()).isEqualTo(2);
    }

    @Test
    void queriesIdentitiesOnlyForRecentArticleVisits() {
        List<AnalyticsVisitEntity> visits = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(minute -> visit("/thoughts/example-" + minute + ".html", "visitor-" + minute, 5_000, 50, minute))
                .toList();
        when(visitRepository.findByStartedAtAfter(any(OffsetDateTime.class))).thenReturn(visits);

        service.articleMetrics(30, "", "visits", 1, 12);

        ArgumentCaptor<Collection<String>> captor = visitorIdCollectionCaptor();
        verify(identityRepository).findByVisitorIdIn(captor.capture());
        assertThat(captor.getValue()).hasSize(20);
        assertThat(captor.getValue()).contains("visitor-25").doesNotContain("visitor-1");
    }

    @Test
    void queriesIdentitiesOnlyForRecentMusicSessions() {
        List<MusicListenSessionEntity> sessions = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(minute -> listen("session-" + minute, "song-" + minute, "歌曲" + minute, "visitor-" + minute, minute))
                .toList();
        when(musicRepository.findByStartedAtAfter(any(OffsetDateTime.class))).thenReturn(sessions);

        service.musicAnalytics(30);

        ArgumentCaptor<Collection<String>> captor = visitorIdCollectionCaptor();
        verify(identityRepository).findByVisitorIdIn(captor.capture());
        assertThat(captor.getValue()).hasSize(20);
        assertThat(captor.getValue()).contains("visitor-25").doesNotContain("visitor-1");
    }

    @Test
    void queriesIdentitiesOnlyForTopEncouragementVisitors() {
        List<EncouragementEventEntity> events = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(minute -> encouragement("visitor-" + minute, minute, minute))
                .toList();
        when(visitRepository.findByStartedAtAfter(any(OffsetDateTime.class))).thenReturn(List.of());
        when(encouragementRepository.findByCreatedAtAfter(any(OffsetDateTime.class))).thenReturn(events);

        service.summary();

        ArgumentCaptor<Collection<String>> captor = visitorIdCollectionCaptor();
        verify(identityRepository).findByVisitorIdIn(captor.capture());
        assertThat(captor.getValue()).hasSize(20);
        assertThat(captor.getValue()).contains("visitor-25").doesNotContain("visitor-1");
    }

    private AnalyticsVisitEntity visit(
            String path,
            String visitorId,
            long durationMs,
            int scrollPercent,
            int minute
    ) {
        AnalyticsVisitEntity entity = new AnalyticsVisitEntity();
        entity.setVisitId("visit-" + minute);
        entity.setPath(path);
        entity.setTitle("示例文章");
        entity.setPageType(path.contains("/thoughts/") ? "article" : "page");
        entity.setVisitorId(visitorId);
        entity.setIp("203.0.113." + minute);
        entity.setUserAgent("Mozilla/5.0 Windows Chrome/120");
        entity.setReferrer("https://example.com/source");
        entity.setStartedAt(OffsetDateTime.parse("2026-06-24T12:" + String.format("%02d", minute) + ":00+08:00"));
        entity.setDurationMs(durationMs);
        entity.setMaxScrollPercent(scrollPercent);
        return entity;
    }

    private MusicListenSessionEntity listen(
            String sessionId,
            String songId,
            String songName,
            String visitorId,
            int minute
    ) {
        MusicListenSessionEntity entity = new MusicListenSessionEntity();
        entity.setListenSessionId(sessionId);
        entity.setVisitorId(visitorId);
        entity.setIp("203.0.113." + minute);
        entity.setSongId(songId);
        entity.setSongName(songName);
        entity.setArtist("歌手");
        entity.setPlaybackSource("global");
        entity.setUrlSource("public");
        entity.setPagePath("/");
        entity.setStartedAt(OffsetDateTime.parse("2026-06-24T13:" + String.format("%02d", minute) + ":00+08:00"));
        entity.setUpdatedAt(entity.getStartedAt());
        entity.setListenedMs(60_000);
        entity.setDurationMs(120_000);
        entity.setCompleted(false);
        return entity;
    }

    private EncouragementEventEntity encouragement(String visitorId, int delta, int minute) {
        EncouragementEventEntity entity = new EncouragementEventEntity();
        entity.setVisitorId(visitorId);
        entity.setIp("203.0.113." + minute);
        entity.setUserAgent("Mozilla/5.0 Windows Chrome/120");
        entity.setDelta(delta);
        entity.setCreatedAt(OffsetDateTime.parse("2026-06-24T14:" + String.format("%02d", minute) + ":00+08:00"));
        return entity;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Collection<String>> visitorIdCollectionCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    private AdminAnalyticsService createService(String excludedVisitorNames, String excludedWalineUserIds) {
        return new AdminAnalyticsService(
                visitRepository,
                encouragementRepository,
                identityRepository,
                musicRepository,
                contentCatalogService,
                new AnalyticsPathPolicy(),
                ipRegionService,
                visitorIdentityService,
                articleMetricService,
                "Asia/Shanghai",
                excludedVisitorNames,
                excludedWalineUserIds
        );
    }
}
