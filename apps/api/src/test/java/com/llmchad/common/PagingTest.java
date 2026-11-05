package com.llmchad.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PagingTest {

    @Test
    void nullValues_fallBackToDefaults() {
        Paging paging = new Paging(null, null);

        assertThat(paging.size()).isEqualTo(20);
        assertThat(paging.page()).isEqualTo(1);
    }

    @Test
    void givenValues_areKept() {
        Paging paging = new Paging(50, 3);

        assertThat(paging.size()).isEqualTo(50);
        assertThat(paging.page()).isEqualTo(3);
    }
}
