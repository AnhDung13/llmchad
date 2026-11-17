package com.llmchad.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TitleServiceTest {

    @Test
    void clean_stripsQuotesMarkdownAndTrailingPunctuation() {
        assertThat(TitleService.clean("\"Giá vàng hôm nay\".")).isEqualTo("Giá vàng hôm nay");
        assertThat(TitleService.clean("**Spring Boot là gì?**")).isEqualTo("Spring Boot là gì?");
        assertThat(TitleService.clean("“Học Spring trong 4 tuần”")).isEqualTo("Học Spring trong 4 tuần");
    }

    @Test
    void clean_takesFirstNonBlankLineAndDropsPrefix() {
        assertThat(TitleService.clean("\n\nTitle: JWT basics\nThis chat is about...")).isEqualTo("JWT basics");
        assertThat(TitleService.clean("Tiêu đề: Tỷ giá USD")).isEqualTo("Tỷ giá USD");
    }

    @Test
    void clean_collapsesWhitespaceAndCutsLongTitles() {
        assertThat(TitleService.clean("  Giá   xăng \t RON95  ")).isEqualTo("Giá xăng RON95");
        assertThat(TitleService.clean("a".repeat(120))).hasSize(TitleService.MAX_TITLE_CHARS + 1).endsWith("…");
    }

    @Test
    void clean_emptyOrNull_returnsNull() {
        assertThat(TitleService.clean(null)).isNull();
        assertThat(TitleService.clean("  \n \"\" ")).isNull();
    }
}
