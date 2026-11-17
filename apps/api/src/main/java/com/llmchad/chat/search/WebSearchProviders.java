package com.llmchad.chat.search;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Tập các provider đã có API key. Spring inject mọi bean implement WebSearchProvider vào List. */
@Component
public class WebSearchProviders {
    private final List<WebSearchProvider> enabled;

    public WebSearchProviders(List<WebSearchProvider> providers) {
        this.enabled = providers.stream().filter(WebSearchProvider::isEnabled).toList();
    }

    /** Provider theo id; rỗng nếu id null, không tồn tại hoặc chưa có key (coi như tắt web search) */
    public Optional<WebSearchProvider> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return enabled.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public List<String> enabledIds() {
        return enabled.stream().map(WebSearchProvider::id).toList();
    }
}
