package com.lycanclaw.backend.music.controller;

import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.music.dto.MusicTrackLyricDto;
import com.lycanclaw.backend.music.service.MusicDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开音乐数据接口。
 * 提供排行榜、歌曲详情和歌词查询。
 * @author Wreckloud
 * @since 2026-05-15
 */
@RestController
@RequestMapping("/api/music")
@Tag(name = "音乐播放", description = "音乐排行、歌曲详情和歌词查询")
public class MusicController {

    private final MusicDataService musicDataService;

    public MusicController(MusicDataService musicDataService) {
        this.musicDataService = musicDataService;
    }

    /**
     * 查询配置账号的周听歌榜。
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
     * 查询歌曲原文歌词时间轴。
     */
    @Operation(summary = "获取歌曲歌词")
    @GetMapping("/track/lyric")
    public ApiResponse<MusicTrackLyricDto> trackLyric(
            @Parameter(description = "歌曲 ID", required = true)
            @RequestParam("id") String id
    ) {
        return ApiResponse.ok(musicDataService.getTrackLyric(id));
    }

}
