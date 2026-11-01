package com.llmchat.chat.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.llmchat.chat.dto.LLMMessage;
import com.llmchat.chat.dto.LLMRequestDTO;
import com.llmchat.chat.dto.MessageRequest;
import com.llmchat.chat.model.Message;
import com.llmchat.chat.repository.ConversationRepository;
import com.llmchat.chat.repository.MessageRepositoriy;
import com.llmchat.common.MessageKey;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class StreamingService {
    private static final Logger log = LoggerFactory.getLogger(StreamingService.class);
    private static final Duration STREAM_TTL = Duration.ofMinutes(5);

    private final RestClient llmRestClient;
    private final MessageRepositoriy messageRepositoriy;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final TaskExecutor executor;
    private final String model;

    public StreamingService(MessageRepositoriy messageRepositoriy, ConversationRepository conversationRepository,
            ConversationService conversationService, RestClient llmRestClient, ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate, TaskExecutor executor, @Value("${app.llm.model}") String model) {
        this.messageRepositoriy = messageRepositoriy;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.llmRestClient = llmRestClient;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.executor = executor;
        this.model = model;
    }

    public SseEmitter stream(UUID userId, UUID convoId, MessageRequest req) {

        conversationService.getConversation(userId, convoId);
        Message userMessage = messageRepositoriy.insertMessage(convoId, "user", req.content(), null);
        List<LLMMessage> history = messageRepositoriy.getMessages(convoId).stream()
                .map(m -> new LLMMessage(m.role(), m.content()))
                .toList();

        String key = "chat:stream:" + userMessage.id();

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean stopped = new AtomicBoolean(false);
        emitter.onCompletion(() -> stopped.set(true));
        emitter.onTimeout(() -> stopped.set(true));
        emitter.onError(e -> stopped.set(true));

        executor.execute(() -> openStream(key, convoId, history, stopped));
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

    private void openStream(String key, UUID convoId, List<LLMMessage> history, AtomicBoolean stopped) {
        LLMRequestDTO llmReq = new LLMRequestDTO(model, true, history);
        try {
            llmRestClient.post().uri("/chat/completions").body(llmReq).exchange((request, response) -> {
                if (response.getStatusCode().isError()) {
                    log.error("LLM returned {}: {}", response.getStatusCode(),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
                    addError(key);
                    return null;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8));
                String line;
                StringBuilder fullText = new StringBuilder();
                StringBuilder fullReasoning = new StringBuilder();

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
                        fullText.append(text);
                        redisTemplate.opsForStream().add(key, Map.of("type", "delta", "text", text));
                    }
                }

                Message assistantMessage = messageRepositoriy.insertMessage(convoId, "assistant", fullText.toString(),
                        fullReasoning.isEmpty() ? null : fullReasoning.toString());
                conversationRepository.touchConversation(convoId);
                redisTemplate.opsForStream().add(key,
                        Map.of("type", "done", "message", objectMapper.writeValueAsString(assistantMessage)));
                return null;
            });
        } catch (RuntimeException e) {
            log.error("LLM stream {} failed", key, e);
            addError(key);
        } finally {
            redisTemplate.expire(key, STREAM_TTL);
        }
    }

    private static String textOf(JsonNode node) {
        return node.isString() && !node.stringValue().isEmpty() ? node.stringValue() : null;
    }

    private void addError(String key) {
        redisTemplate.opsForStream().add(key, Map.of("type", "error"));
    }

}
