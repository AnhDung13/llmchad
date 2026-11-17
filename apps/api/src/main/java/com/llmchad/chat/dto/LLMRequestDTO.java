package com.llmchad.chat.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** `tools` null thì không gửi (model không gọi tool được) */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LLMRequestDTO(String model, Boolean stream, List<LLMMessage> messages, List<Map<String, Object>> tools) {

    public LLMRequestDTO(String model, Boolean stream, List<LLMMessage> messages) {
        this(model, stream, messages, null);
    }
}
