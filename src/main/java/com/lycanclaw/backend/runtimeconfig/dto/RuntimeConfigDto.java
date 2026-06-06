package com.lycanclaw.backend.runtimeconfig.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 后台可编辑运行时配置。
 * 用于保存不涉及密钥的音乐、推荐与标签配置，敏感配置仍由环境变量或 application.yml 管理。
 * @author Wreckloud
 * @since 2026-06-03
 */
@Schema(description = "后台可编辑运行时配置")
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeConfigDto(
        @Schema(description = "音乐运行配置")
        Music music,
        @Schema(description = "推荐运行配置")
        Recommendation recommendation,
        @Schema(description = "标签运行配置")
        Tag tag
) {

    @Schema(description = "音乐运行配置")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Music(
            @Schema(description = "随机流与关于页榜单来源 UID", example = "629126546")
            String playlistOwnerUid,
            @Schema(description = "默认音质", example = "exhigh")
            String preferredLevel
    ) {
    }

    @Schema(description = "推荐运行配置")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recommendation(
            @Schema(description = "最大候选文章数", example = "200")
            Integer maxCandidatePosts,
            @Schema(description = "推荐快照保鲜秒数", example = "900")
            Integer snapshotFreshSeconds,
            @Schema(description = "浏览量权重", example = "1.0")
            Double pageviewWeight,
            @Schema(description = "评论数权重", example = "24.0")
            Double commentWeight
    ) {
    }

    @Schema(description = "标签运行配置")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tag(
            @Schema(description = "标签缓存秒数", example = "300")
            Integer tagCacheSeconds
    ) {
    }
}
