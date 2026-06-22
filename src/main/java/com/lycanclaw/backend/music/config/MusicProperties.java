package com.lycanclaw.backend.music.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lycan.music")
public class MusicProperties {

    private String playlistOwnerUid = "629126546";
    private String preferredLevel = "exhigh";

    public String getPlaylistOwnerUid() {
        return playlistOwnerUid;
    }

    public void setPlaylistOwnerUid(String playlistOwnerUid) {
        this.playlistOwnerUid = playlistOwnerUid;
    }

    public String getPreferredLevel() {
        return preferredLevel;
    }

    public void setPreferredLevel(String preferredLevel) {
        this.preferredLevel = preferredLevel;
    }
}
