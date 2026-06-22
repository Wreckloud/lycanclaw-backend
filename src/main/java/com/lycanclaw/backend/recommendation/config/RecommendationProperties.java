package com.lycanclaw.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lycan.recommendation")
public class RecommendationProperties {

    private int maxCandidatePosts = 200;
    private Score score = new Score();

    public int getMaxCandidatePosts() {
        return maxCandidatePosts;
    }

    public void setMaxCandidatePosts(int maxCandidatePosts) {
        this.maxCandidatePosts = maxCandidatePosts;
    }

    public Score getScore() {
        return score;
    }

    public void setScore(Score score) {
        this.score = score;
    }

    public static class Score {

        private double pageviewWeight = 1.0D;
        private double commentWeight = 24.0D;

        public double getPageviewWeight() {
            return pageviewWeight;
        }

        public void setPageviewWeight(double pageviewWeight) {
            this.pageviewWeight = pageviewWeight;
        }

        public double getCommentWeight() {
            return commentWeight;
        }

        public void setCommentWeight(double commentWeight) {
            this.commentWeight = commentWeight;
        }
    }
}
