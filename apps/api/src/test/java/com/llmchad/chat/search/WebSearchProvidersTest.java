package com.llmchad.chat.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class WebSearchProvidersTest {

    private record FakeProvider(String id, boolean isEnabled) implements WebSearchProvider {
        @Override
        public List<SearchResult> search(String query) {
            return List.of();
        }
    }

    private final WebSearchProviders providers = new WebSearchProviders(List.of(
            new FakeProvider("tavily", true), new FakeProvider("tinyfish", false)));

    @Test
    void enabledIds_onlyProvidersWithKey() {
        assertThat(providers.enabledIds()).containsExactly("tavily");
    }

    @Test
    void find_enabledProvider() {
        assertThat(providers.find("tavily")).map(WebSearchProvider::id).contains("tavily");
    }

    @Test
    void find_disabledUnknownOrNull_isEmpty() {
        assertThat(providers.find("tinyfish")).isEmpty();
        assertThat(providers.find("google")).isEmpty();
        assertThat(providers.find(null)).isEmpty();
    }
}
