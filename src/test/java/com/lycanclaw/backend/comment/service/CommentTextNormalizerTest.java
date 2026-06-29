package com.lycanclaw.backend.comment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评论文本规范化测试。
 * 验证前台和后台列表展示所需的纯文本摘要。
 * @author Wreckloud
 * @since 2026-06-29
 */
class CommentTextNormalizerTest {

    private final CommentTextNormalizer normalizer = new CommentTextNormalizer();

    @Test
    void replacesMarkdownImageWithPlaceholder() {
        String text = normalizer.toPlainText("我也喜欢你! ![3e9be783193ac0139d1906eb04ab82f3.jpg](data:image/webp;base64,abc)");

        assertThat(text).isEqualTo("我也喜欢你! [图片]");
    }

    @Test
    void keepsReadableTextForCommonMarkdown() {
        String text = normalizer.toPlainText("**你好**，[主页](https://example.com) `code`");

        assertThat(text).isEqualTo("你好，主页 code");
    }
}
