package com.llmchad.chat.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Một tin trong `messages` gửi cho LLM. Tin thường chỉ có role + content; tin assistant gọi tool có thêm
 * tool_calls; tin role=tool trả kết quả có tool_call_id. Field null thì không gửi.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LLMMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId) {

    public LLMMessage(String role, String content) {
        this(role, content, null, null);
    }

    public static LLMMessage assistantToolCalls(String content, List<ToolCall> toolCalls) {
        return new LLMMessage("assistant", content, toolCalls, null);
    }

    public static LLMMessage toolResult(String toolCallId, String content) {
        return new LLMMessage("tool", content, null, toolCallId);
    }
}
