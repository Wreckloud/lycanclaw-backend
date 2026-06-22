package com.lycanclaw.backend.common.path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebPathNormalizerTest {

    @Test
    void decodesArticlePathAndRemovesQueryAndFragment() {
        String normalized = WebPathNormalizer.normalize(
                "thoughts/%E7%8C%AE%E7%BB%99%E7%8B%BC.html?from=home#comments"
        );

        assertThat(normalized).isEqualTo("/thoughts/献给狼.html");
    }
}
