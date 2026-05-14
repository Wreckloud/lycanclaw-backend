package com.lycanclaw.backend.music.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.music.dto.MusicQueueSnapshotDto;
import com.lycanclaw.backend.music.dto.QueueEnqueueRequest;
import com.lycanclaw.backend.music.dto.QueueRemoveRequest;
import com.lycanclaw.backend.music.dto.QueueSetCurrentRequest;
import com.lycanclaw.backend.music.service.MusicDataService;
import com.lycanclaw.backend.music.service.MusicQueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/music")
public class MusicController {

    private final MusicDataService musicDataService;
    private final MusicQueueService musicQueueService;

    public MusicController(MusicDataService musicDataService, MusicQueueService musicQueueService) {
        this.musicDataService = musicDataService;
        this.musicQueueService = musicQueueService;
    }

    @GetMapping("/ranking/weekly")
    public ApiResponse<Map<String, Object>> weeklyRanking(
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(musicDataService.getWeeklyRanking(limit));
    }

    @GetMapping("/track/url")
    public ApiResponse<Map<String, Object>> trackUrl(
            @RequestParam("id") String id,
            @RequestParam(name = "level", required = false) String level
    ) {
        return ApiResponse.ok(musicDataService.getTrackUrl(id, level));
    }

    @GetMapping("/track/detail-with-url")
    public ApiResponse<Map<String, Object>> trackDetailWithUrl(
            @RequestParam("id") String id,
            @RequestParam(name = "level", required = false) String level
    ) {
        return ApiResponse.ok(musicDataService.getTrackDetailWithUrl(id, level));
    }

    @GetMapping("/queue")
    public ApiResponse<MusicQueueSnapshotDto> queueSnapshot(
            @RequestParam(name = "limit", defaultValue = "30") int limit
    ) {
        return ApiResponse.ok(musicQueueService.snapshot(limit));
    }

    @PostMapping("/queue/enqueue")
    public ApiResponse<Map<String, Object>> enqueue(@RequestBody QueueEnqueueRequest request) {
        return ApiResponse.ok(musicQueueService.enqueue(request));
    }

    @PostMapping("/queue/next")
    public ApiResponse<Map<String, Object>> playNext() {
        return ApiResponse.ok(musicQueueService.playNext());
    }

    @PostMapping("/queue/current")
    public ApiResponse<Map<String, Object>> setCurrent(@RequestBody QueueSetCurrentRequest request) {
        boolean resumeCurrent = request.resumeCurrent() == null || request.resumeCurrent();
        return ApiResponse.ok(musicQueueService.setCurrentByQueueId(request.queueId(), resumeCurrent));
    }

    @PostMapping("/queue/remove")
    public ApiResponse<Map<String, Object>> removeQueueItem(@RequestBody QueueRemoveRequest request) {
        return ApiResponse.ok(musicQueueService.removeByQueueId(request.queueId()));
    }

    @PostMapping("/queue/clear")
    public ApiResponse<Map<String, Object>> clearQueue(
            @RequestParam(name = "keepCurrent", defaultValue = "true") boolean keepCurrent
    ) {
        return ApiResponse.ok(musicQueueService.clear(keepCurrent));
    }
}
