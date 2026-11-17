package com.llmchad.chat.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/** Gọi TinyFish Search API (https://docs.tinyfish.ai/search-api/reference): GET ?query=..., key ở header X-API-Key */
@Component
public class TinyFishClient implements WebSearchProvider {
    private final RestClient client;
    private final boolean enabled;
    private final int maxResults;

    public TinyFishClient(@Value("${app.tinyfish.base-url}") String baseUrl,
            @Value("${app.tinyfish.api-key:}") String apiKey,
            @Value("${app.tinyfish.max-results:5}") int maxResults) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-API-Key", apiKey)
                .build();
        this.enabled = !apiKey.isBlank();
        this.maxResults = maxResults;
    }

    @Override
    public String id() {
        return "tinyfish";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<SearchResult> search(String query) {
        // Truyền query qua biến URI để RestClient encode (tiếng Việt, dấu &, khoảng trắng...)
        JsonNode body = client.get()
                .uri(b -> b.queryParam("query", "{query}").build(query))
                .retrieve()
                .body(JsonNode.class);

        List<SearchResult> results = new ArrayList<>();
        if (body == null) {
            return results;
        }
        // TinyFish không có tham số giới hạn số kết quả (mỗi trang ~10), nên tự cắt
        for (JsonNode r : body.path("results")) {
            if (results.size() >= maxResults) {
                break;
            }
            String url = r.path("url").asString("");
            if (!url.isBlank()) {
                results.add(new SearchResult(r.path("title").asString(""), url, r.path("snippet").asString("")));
            }
        }
        return results;
    }
}
