package com.llmchad.chat.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

@Component
public class TavilyClient implements WebSearchProvider {
    private final RestClient client;
    private final boolean enabled;
    private final int maxResults;

    public TavilyClient(@Value("${app.tavily.base-url}") String baseUrl,
            @Value("${app.tavily.api-key:}") String apiKey,
            @Value("${app.tavily.max-results:5}") int maxResults) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.enabled = !apiKey.isBlank();
        this.maxResults = maxResults;
    }

    @Override
    public String id() {
        return "tavily";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<SearchResult> search(String query) {
        JsonNode body = client.post().uri("/search")
                .body(Map.of("query", query, "max_results", maxResults))
                .retrieve()
                .body(JsonNode.class);

        List<SearchResult> results = new ArrayList<>();
        if (body == null) {
            return results;
        }
        for (JsonNode r : body.path("results")) {
            String url = r.path("url").asString("");
            if (!url.isBlank()) {
                results.add(new SearchResult(r.path("title").asString(""), url, r.path("content").asString("")));
            }
        }
        return results;
    }
}
