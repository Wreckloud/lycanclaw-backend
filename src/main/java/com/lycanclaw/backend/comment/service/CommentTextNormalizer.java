package com.lycanclaw.backend.comment.service;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 评论文本规范化工具。
 * 将 Waline 原文或 HTML 评论转换为适合后台列表展示的纯文本。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Component
public class CommentTextNormalizer {

    /**
     * 保留段落换行并移除 HTML 标签和多余空白。
     */
    public String toPlainText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("<[^>]+>", "");
        text = HtmlUtils.htmlUnescape(text);
        return text.replace('\u00A0', ' ')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\s*\\n\\s*", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
