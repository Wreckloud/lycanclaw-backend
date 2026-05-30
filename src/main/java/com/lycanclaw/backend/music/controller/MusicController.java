package com.lycanclaw.backend.music.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.music.dto.MusicQueueSnapshotDto;
import com.lycanclaw.backend.music.dto.QueueEnqueueRequest;
import com.lycanclaw.backend.music.service.MusicDataService;
import com.lycanclaw.backend.music.service.MusicQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 音乐接口控制器。
 * 用于提供音乐相关 REST 接口。
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/music")
@Tag(name = "音乐播放", description = "音乐数据查询与播放队列控制")
public class MusicController {

    private final MusicDataService musicDataService;
    private final MusicQueueService musicQueueService;

    public MusicController(MusicDataService musicDataService, MusicQueueService musicQueueService) {
        this.musicDataService = musicDataService;
        this.musicQueueService = musicQueueService;
    }

    /**
     * 查询当前账号（或回退账号）的周听歌榜。
     */
    @Operation(summary = "查询周听歌榜")
    @GetMapping("/ranking/weekly")
    public ApiResponse<Map<String, Object>> weeklyRanking(
            @Parameter(description = "最多返回条数，范围 1-100")
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(musicDataService.getWeeklyRanking(limit));
    }

    /**
     * 按歌曲 id 获取可播放 URL，会按音质降级尝试。
     */
    @Operation(summary = "获取歌曲播放地址")
    @GetMapping("/track/url")
    public ApiResponse<Map<String, Object>> trackUrl(
            @Parameter(description = "歌曲 ID", required = true)
            @RequestParam("id") String id,
            @Parameter(description = "期望音质级别，如 jymaster / exhigh / standard")
            @RequestParam(name = "level", required = false) String level
    ) {
        return ApiResponse.ok(musicDataService.getTrackUrl(id, level));
    }

    /**
     * 获取歌曲详情并附带可播放 URL。
     */
    @Operation(summary = "获取歌曲详情和播放地址")
    @GetMapping("/track/detail-with-url")
    public ApiResponse<Map<String, Object>> trackDetailWithUrl(
            @Parameter(description = "歌曲 ID", required = true)
            @RequestParam("id") String id,
            @Parameter(description = "期望音质级别，如 jymaster / exhigh / standard")
            @RequestParam(name = "level", required = false) String level
    ) {
        return ApiResponse.ok(musicDataService.getTrackDetailWithUrl(id, level));
    }

    /**
     * 获取播放队列快照（当前播放 + 下一批预览）。
     */
    @Operation(summary = "获取播放队列快照")
    @GetMapping("/queue")
    public ApiResponse<MusicQueueSnapshotDto> queueSnapshot(
            @Parameter(description = "返回预览条数，范围 1-3")
            @RequestParam(name = "limit", defaultValue = "3") int limit
    ) {
        return ApiResponse.ok(musicQueueService.snapshot(limit));
    }

    /**
     * 歌曲入队：当前版本仅支持追加到队尾。
     */
    @Operation(summary = "歌曲入队")
    @PostMapping("/queue/enqueue")
    public ApiResponse<Map<String, Object>> enqueue(@RequestBody QueueEnqueueRequest request) {
        return ApiResponse.ok(musicQueueService.enqueue(request));
    }

    /**
     * 播放下一首（队列头部提升为 current）。
     */
    @Operation(summary = "切到下一首")
    @PostMapping("/queue/next")
    public ApiResponse<Map<String, Object>> playNext() {
        return ApiResponse.ok(musicQueueService.playNext());
    }

    /**
     * 清空等待队列，可选择保留 current。
     */
    @Operation(summary = "清空队列")
    @PostMapping("/queue/clear")
    public ApiResponse<Map<String, Object>> clearQueue(
            @Parameter(description = "是否保留当前播放项")
            @RequestParam(name = "keepCurrent", defaultValue = "true") boolean keepCurrent
    ) {
        return ApiResponse.ok(musicQueueService.clear(keepCurrent));
    }
}
