package com.llmchad.chat.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.llmchad.chat.dto.LLMMessage;

import tools.jackson.databind.JsonNode;

/** Đặt tên conversation từ tin nhắn đầu tiên: gọi LLM một lần, không stream */
@Service
public class TitleService {
    private static final Logger log = LoggerFactory.getLogger(TitleService.class);
    /** Ngắn hơn giới hạn 200 ký tự của title để vừa sidebar */
    static final int MAX_TITLE_CHARS = 80;
    /** Tin nhắn dài chỉ cần phần đầu là đủ đoán chủ đề, lại đỡ tốn token */
    private static final int MAX_INPUT_CHARS = 1000;
    private static final String PROMPT = """
            Write a short title (at most 6 words) for a chat that starts with the user's message below. \
            Use the same language as the message. Reply with the title only: no quotes, no trailing period, no emoji.""";

    private final RestClient llmRestClient;
    private final String model;

    public TitleService(RestClient llmRestClient, @Value("${app.llm.model}") String model) {
        this.llmRestClient = llmRestClient;
        this.model = model;
    }

    /** Rỗng nếu LLM lỗi hoặc trả về chữ không dùng được: conversation giữ tên cũ */
    public Optional<String> generate(String userMessage) {
        String input = userMessage.length() > MAX_INPUT_CHARS ? userMessage.substring(0, MAX_INPUT_CHARS) : userMessage;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", List.of(new LLMMessage("system", PROMPT), new LLMMessage("user", input)));
        // Model suy luận (gpt-oss) mặc định nghĩ khá lâu; đặt tên không cần nghĩ nhiều
        body.put("reasoning_effort", "low");
        try {
            JsonNode res = llmRestClient.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
            String raw = res == null ? null : res.path("choices").path(0).path("message").path("content").asString(null);
            return Optional.ofNullable(clean(raw));
        } catch (RestClientException e) {
            log.warn("Generate title failed", e);
            return Optional.empty();
        }
    }

    /** Lấy dòng đầu, bỏ ngoặc kép, dấu **, tiền tố "Title:", dấu câu cuối; null nếu không còn chữ */
    static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String title = raw.strip().lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
        title = title.replaceFirst("(?i)^(title|tiêu đề)\\s*:\\s*", "")
                // Giữ dấu ? ở cuối (tiêu đề dạng câu hỏi), bỏ ngoặc kép, ** và dấu câu khác ở hai đầu
                .replaceAll("^[\"'“”‘’*#`\\s]+|[\"'“”‘’*`\\s.。!！:;,]+$", "")
                .replaceAll("\\s+", " ")
                .strip();
        if (title.length() > MAX_TITLE_CHARS) {
            title = title.substring(0, MAX_TITLE_CHARS).strip() + "…";
        }
        return title.isEmpty() ? null : title;
    }
}
