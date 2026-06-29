package com.lycanclaw.backend.music.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 音乐业务配置。
 * 定义排行榜账号和默认播放音质。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@ConfigurationProperties(prefix = "lycan.music")
public class MusicProperties {

    private String rankingOwnerUid = "";
    private String preferredLevel = "exhigh";

    public String getRankingOwnerUid() {
        return rankingOwnerUid;
    }

    public void setRankingOwnerUid(String rankingOwnerUid) {
        this.rankingOwnerUid = rankingOwnerUid;
    }

    public String getPreferredLevel() {
        return preferredLevel;
    }

    public void setPreferredLevel(String preferredLevel) {
        this.preferredLevel = preferredLevel;
    }
}
