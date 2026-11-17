package com.llmchad.chat.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.llmchad.chat.dto.LLMMessage;
import com.llmchad.chat.dto.LLMRequestDTO;
import com.llmchad.chat.dto.MessageRequest;
import com.llmchad.chat.dto.Source;
import com.llmchad.chat.dto.ToolCall;
import com.llmchad.chat.model.Message;
import com.llmchad.chat.repository.ConversationRepository;
import com.llmchad.chat.repository.MessageRepositoriy;
import com.llmchad.chat.search.SearchResult;
import com.llmchad.chat.search.WebSearchProvider;
import com.llmchad.chat.search.WebSearchProviders;
import com.llmchad.common.MessageKey;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class StreamingService {
    private static final Logger log = LoggerFactory.getLogger(StreamingService.class);
    private static final Duration STREAM_TTL = Duration.ofMinutes(5);
    /** Số lần gọi LLM tối đa trong một lượt trả lời. Vòng cuối không gửi tools để model buộc phải trả lời */
    private static final int MAX_ROUNDS = 3;
    private static final String WEB_SEARCH = "web_search";
    /** Vòng cuối không có tools: không nhắc thì model hay vẫn muốn tìm tiếp và trả về rỗng */
    private static final String FINAL_ROUND_PROMPT = """
            You cannot search any more. Answer the user now using the search results above. \
            If they are not enough, say briefly what you found and what is missing.""";
    private static final List<Map<String, Object>> TOOLS = List.of(Map.of(
            "type", "function",
            "function", Map.of(
                    "name", WEB_SEARCH,
                    "description", "Search the web for up-to-date information. Returns a list of pages with title, url and an excerpt.",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of("query", Map.of(
                                    "type", "string",
                                    "description", "Search query, in the language most likely to find good results")),
                            "required", List.of("query")))));

    private final RestClient llmRestClient;
    private final MessageRepositoriy messageRepositoriy;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final TaskExecutor executor;
    private final WebSearchProviders searchProviders;
    private final String model;

    public StreamingService(MessageRepositoriy messageRepositoriy, ConversationRepository conversationRepository,
            ConversationService conversationService, RestClient llmRestClient, ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate, TaskExecutor executor, WebSearchProviders searchProviders,
            @Value("${app.llm.model}") String model) {
        this.messageRepositoriy = messageRepositoriy;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.llmRestClient = llmRestClient;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.executor = executor;
        this.searchProviders = searchProviders;
        this.model = model;
    }

    public SseEmitter stream(UUID userId, UUID convoId, MessageRequest req) {

        conversationService.getConversation(userId, convoId);
        Message userMessage = messageRepositoriy.insertMessage(convoId, "user", req.content(), null);
        WebSearchProvider searchProvider = searchProviders.find(req.searchProvider()).orElse(null);
        List<LLMMessage> history = new ArrayList<>();
        if (searchProvider != null) {
            history.add(new LLMMessage("system", webSearchPrompt()));
        }
        messageRepositoriy.getMessages(convoId).forEach(m -> history.add(new LLMMessage(m.role(), m.content())));

        String key = "chat:stream:" + userMessage.id();

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean stopped = new AtomicBoolean(false);
        emitter.onCompletion(() -> stopped.set(true));
        emitter.onTimeout(() -> stopped.set(true));
        emitter.onError(e -> stopped.set(true));

        executor.execute(() -> openStream(key, convoId, history, searchProvider, stopped));
        executor.execute(() -> readStream(key, emitter, stopped));

        return emitter;
    }

    private void readStream(String key, SseEmitter emitter, AtomicBoolean stopped) {
        String lastId = "0";
        try {
            while (!stopped.get()) {
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                        .read(StreamReadOptions.empty().block(Duration.ofSeconds(2)),
                                StreamOffset.create(key, ReadOffset.from(lastId)));

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    lastId = record.getId().getValue();
                    Map<Object, Object> fields = record.getValue();
                    String type = String.valueOf(fields.get("type"));

                    switch (type) {
                        case "reasoning", "delta" ->
                            emitter.send(SseEmitter.event().name(type).data(Map.of("text", fields.get("text"))));
                        case "search" ->
                            emitter.send(SseEmitter.event().name("search").data(Map.of("query", fields.get("query"))));
                        // Đã là chuỗi JSON {"sources": [...]}, gửi nguyên văn như "done"
                        case "sources" -> emitter.send(SseEmitter.event().name("sources").data(fields.get("sources")));
                        case "done" -> {
                            emitter.send(SseEmitter.event().name("done").data(fields.get("message")));
                            emitter.complete();
                            return;
                        }
                        case "error" -> {
                            emitter.send(SseEmitter.event().name("error")
                                    .data(Map.of("message", MessageKey.LLM_FAILED)));
                            emitter.complete();
                            return;
                        }
                        default -> log.warn("Unknown record type {} in {}", type, key);
                    }
                }
            }
        } catch (IOException e) {
            stopped.set(true);
        } catch (RuntimeException e) {
            log.error("Read stream {} failed", key, e);
            stopped.set(true);
            emitter.completeWithError(e);
        }
    }

    /**
     * Một lượt trả lời: gọi LLM, nếu model gọi web_search thì tìm bằng provider đã chọn, đưa kết quả lại cho model rồi
     * gọi tiếp, đến khi model trả lời mà không gọi tool nữa. Chữ của mọi vòng đều đã stream cho người dùng nên đều được
     * lưu. `searchProvider` null = không cho model gọi tool.
     */
    private void openStream(String key, UUID convoId, List<LLMMessage> history, WebSearchProvider searchProvider,
            AtomicBoolean stopped) {
        List<LLMMessage> messages = new ArrayList<>(history);
        StringBuilder fullText = new StringBuilder();
        StringBuilder fullReasoning = new StringBuilder();
        List<Source> sources = new ArrayList<>();
        try {
            for (int round = 0; round < MAX_ROUNDS; round++) {
                // Người dùng dừng trong lúc đang search → không gọi LLM thêm vòng nữa
                if (stopped.get()) {
                    return;
                }
                boolean allowTools = searchProvider != null && round < MAX_ROUNDS - 1;
                List<LLMMessage> request = messages;
                if (searchProvider != null && !allowTools) {
                    request = new ArrayList<>(messages);
                    request.add(new LLMMessage("system", FINAL_ROUND_PROMPT));
                }
                Round result = streamOnce(key, request, allowTools, stopped, fullText, fullReasoning);
                if (result == null) {
                    return; // LLM lỗi (đã gửi error) hoặc người dùng đã dừng
                }
                // Không gửi tools mà model vẫn trả tool_calls (hay gặp ở vòng cuối) → bỏ qua, không search vô ích
                if (result.toolCalls().isEmpty() || !allowTools) {
                    break;
                }

                messages.add(LLMMessage.assistantToolCalls(result.text().isEmpty() ? null : result.text(),
                        result.toolCalls()));
                for (ToolCall call : result.toolCalls()) {
                    if (stopped.get()) {
                        return;
                    }
                    messages.add(LLMMessage.toolResult(call.id(), runTool(key, call, searchProvider, sources)));
                }
                // Model đã viết dở một đoạn trước khi gọi tool → tách đoạn trả lời tiếp theo xuống dòng mới
                if (!fullText.isEmpty() && fullText.charAt(fullText.length() - 1) != '\n') {
                    fullText.append("\n\n");
                    redisTemplate.opsForStream().add(key, Map.of("type", "delta", "text", "\n\n"));
                }
            }

            Message assistantMessage = messageRepositoriy.insertMessage(convoId, "assistant", fullText.toString(),
                    fullReasoning.isEmpty() ? null : fullReasoning.toString(), sourcesJson(sources));
            conversationRepository.touchConversation(convoId);
            redisTemplate.opsForStream().add(key,
                    Map.of("type", "done", "message", objectMapper.writeValueAsString(assistantMessage)));
        } catch (RuntimeException e) {
            log.error("LLM stream {} failed", key, e);
            addError(key);
        } finally {
            redisTemplate.expire(key, STREAM_TTL);
        }
    }

    /** Kết quả một lần gọi LLM: chữ của riêng vòng này và các tool call model muốn chạy */
    private record Round(String text, List<ToolCall> toolCalls) {
    }

    /** Gom các mảnh delta.tool_calls[i] của stream: id, name đến ở mảnh đầu, arguments đến từng mảnh */
    private static final class ToolCallParts {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }

    /** Gọi LLM một lần, đẩy reasoning/delta vào Redis. Trả null nếu LLM lỗi (đã gửi error) hoặc người dùng đã dừng */
    private Round streamOnce(String key, List<LLMMessage> messages, boolean allowTools, AtomicBoolean stopped,
            StringBuilder fullText, StringBuilder fullReasoning) {
        LLMRequestDTO llmReq = new LLMRequestDTO(model, true, messages, allowTools ? TOOLS : null);
        return llmRestClient.post().uri("/chat/completions").body(llmReq).exchange((request, response) -> {
            if (response.getStatusCode().isError()) {
                log.error("LLM returned {}: {}", response.getStatusCode(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
                addError(key);
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8));
            String line;
            StringBuilder roundText = new StringBuilder();
            TreeMap<Integer, ToolCallParts> parts = new TreeMap<>();

            while ((line = reader.readLine()) != null) {
                if (stopped.get()) {
                    return null;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String rawdata = line.substring(5).trim();
                if (rawdata.equals("[DONE]")) {
                    break;
                }

                JsonNode delta = objectMapper.readTree(rawdata).path("choices").path(0).path("delta");
                // Groq / OpenRouter dùng "reasoning", DeepSeek / vLLM dùng "reasoning_content"
                String reasoning = textOf(delta.path("reasoning"));
                if (reasoning == null) {
                    reasoning = textOf(delta.path("reasoning_content"));
                }
                if (reasoning != null) {
                    fullReasoning.append(reasoning);
                    redisTemplate.opsForStream().add(key, Map.of("type", "reasoning", "text", reasoning));
                }
                String text = textOf(delta.path("content"));
                if (text != null) {
                    roundText.append(text);
                    fullText.append(text);
                    redisTemplate.opsForStream().add(key, Map.of("type", "delta", "text", text));
                }
                for (JsonNode tc : delta.path("tool_calls")) {
                    String id = textOf(tc.path("id"));
                    // Vài provider (vd. Gemini) không gửi `index`: mảnh có id mới là call mới, còn lại nối vào call trước
                    int index = tc.has("index") ? tc.path("index").asInt()
                            : parts.isEmpty() || (id != null && !id.equals(parts.lastEntry().getValue().id))
                                    ? parts.size()
                                    : parts.lastKey();
                    ToolCallParts p = parts.computeIfAbsent(index, i -> new ToolCallParts());
                    if (id != null) {
                        p.id = id;
                    }
                    String name = textOf(tc.path("function").path("name"));
                    if (name != null) {
                        p.name = name;
                    }
                    String args = textOf(tc.path("function").path("arguments"));
                    if (args != null) {
                        p.arguments.append(args);
                    }
                }
            }

            List<ToolCall> toolCalls = parts.values().stream()
                    .filter(p -> p.name != null)
                    .map(p -> ToolCall.function(p.id != null ? p.id : "call_" + UUID.randomUUID(), p.name,
                            p.arguments.isEmpty() ? "{}" : p.arguments.toString()))
                    .toList();
            return new Round(roundText.toString(), toolCalls);
        });
    }

    /** Chạy một tool call, trả về nội dung tin role=tool. Lỗi thì báo cho model biết thay vì làm hỏng cả lượt trả lời */
    private String runTool(String key, ToolCall call, WebSearchProvider searchProvider, List<Source> sources) {
        log.info("Stream {} tool call {} {}", key, call.function().name(), call.function().arguments());
        if (!WEB_SEARCH.equals(call.function().name())) {
            return toolError("Unknown tool: " + call.function().name());
        }
        String query;
        try {
            query = objectMapper.readTree(call.function().arguments()).path("query").asString("").trim();
        } catch (RuntimeException e) {
            return toolError("Invalid arguments, expected {\"query\": string}");
        }
        if (query.isEmpty()) {
            return toolError("Missing query");
        }

        redisTemplate.opsForStream().add(key, Map.of("type", "search", "query", query));
        List<SearchResult> results;
        try {
            results = searchProvider.search(query);
        } catch (RestClientException e) {
            log.warn("{} search failed for {}", searchProvider.id(), key, e);
            // Vẫn gửi sources rỗng để frontend tắt dòng "Đang tìm"
            addSources(key, List.of());
            return toolError("Web search failed, answer from your own knowledge and say so");
        }

        List<Source> found = results.stream().map(r -> new Source(r.title(), r.url())).toList();
        sources.addAll(found);
        addSources(key, found);
        return objectMapper.writeValueAsString(Map.of("query", query, "results", results));
    }

    private void addSources(String key, List<Source> found) {
        redisTemplate.opsForStream().add(key,
                Map.of("type", "sources", "sources", objectMapper.writeValueAsString(Map.of("sources", found))));
    }

    private String toolError(String message) {
        return objectMapper.writeValueAsString(Map.of("error", message));
    }

    /** Bỏ trùng theo url (nhiều lần tìm có thể ra cùng trang). null nếu không có nguồn nào */
    private String sourcesJson(List<Source> sources) {
        if (sources.isEmpty()) {
            return null;
        }
        Map<String, Source> byUrl = new LinkedHashMap<>();
        sources.forEach(src -> byUrl.putIfAbsent(src.url(), src));
        return objectMapper.writeValueAsString(byUrl.values());
    }

    private static String webSearchPrompt() {
        return """
                You have a web_search tool. Today is %s (UTC).
                Use it when the question needs recent, time-sensitive or specific factual information you are not sure about; \
                otherwise answer directly. When you use search results, cite them inline as markdown links [title](url). \
                Answer in the language of the user's question.""".formatted(LocalDate.now(ZoneOffset.UTC));
    }

    private static String textOf(JsonNode node) {
        return node.isString() && !node.stringValue().isEmpty() ? node.stringValue() : null;
    }

    private void addError(String key) {
        redisTemplate.opsForStream().add(key, Map.of("type", "error"));
    }

}
