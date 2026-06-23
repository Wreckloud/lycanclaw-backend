package com.lycanclaw.backend.stats.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 文章浏览量和评论数快照。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
@Entity
@Table(name = "article_metrics")
public class ArticleMetricEntity {

    @Id
    @Column(name = "path", nullable = false, length = 255)
    private String path;

    @Column(name = "pageview_count", nullable = false)
    private int pageviewCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getPageviewCount() {
        return pageviewCount;
    }

    public void setPageviewCount(int pageviewCount) {
        this.pageviewCount = pageviewCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

}
