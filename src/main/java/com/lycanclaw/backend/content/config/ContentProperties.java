package com.lycanclaw.backend.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lycan.content")
public class ContentProperties {

    private String postsJsonPath = "../frontend/docs/public/posts.json";

    public String getPostsJsonPath() {
        return postsJsonPath;
    }

    public void setPostsJsonPath(String postsJsonPath) {
        this.postsJsonPath = postsJsonPath;
    }
}
