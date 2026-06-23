package com.lycanclaw.backend.tag.service;

import com.lycanclaw.backend.content.service.ContentCatalogService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 汇总已发布随想的标签数据。
 *
 * @author Wreckloud
 * @since 2026-06-23
 */
@Service
public class TagService {

    private final ContentCatalogService contentCatalogService;

    public TagService(ContentCatalogService contentCatalogService) {
        this.contentCatalogService = contentCatalogService;
    }

    public Map<String, Object> summary() {
        return summarize(contentCatalogService.loadPublishedThoughts());
    }

    public Map<String, Object> refreshCache() {
        return summarize(contentCatalogService.refresh().stream()
                .filter(item -> item.kind() == ContentCatalogService.ContentKind.THOUGHT)
                .toList());
    }

    private Map<String, Object> summarize(List<ContentCatalogService.ContentItem> articles) {
        Set<String> tags = new LinkedHashSet<>();
        for (ContentCatalogService.ContentItem article : articles) {
            tags.addAll(article.tags());
        }
        return Map.of(
                "thoughtPostCount", articles.size(),
                "tagCount", tags.size()
        );
    }
}
