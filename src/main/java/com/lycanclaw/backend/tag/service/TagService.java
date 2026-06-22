package com.lycanclaw.backend.tag.service;

import com.lycanclaw.backend.content.service.ArticleCatalogService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TagService {

    private final ArticleCatalogService articleCatalogService;

    public TagService(ArticleCatalogService articleCatalogService) {
        this.articleCatalogService = articleCatalogService;
    }

    public Map<String, Object> summary() {
        return summarize(articleCatalogService.loadPublishedThoughts());
    }

    public Map<String, Object> refreshCache() {
        return summarize(articleCatalogService.refresh());
    }

    private Map<String, Object> summarize(List<ArticleCatalogService.ArticleCatalogItem> articles) {
        Set<String> tags = new LinkedHashSet<>();
        for (ArticleCatalogService.ArticleCatalogItem article : articles) {
            tags.addAll(article.tags());
        }
        return Map.of(
                "thoughtPostCount", articles.size(),
                "tagCount", tags.size()
        );
    }
}
