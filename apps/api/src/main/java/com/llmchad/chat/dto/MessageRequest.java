package com.llmchad.chat.dto;

import com.llmchad.common.MessageKey;

import jakarta.validation.constraints.NotBlank;

/** `searchProvider`: id provider web search ("tavily", "tinyfish"); null hoặc provider chưa có key thì không search */
public record MessageRequest(@NotBlank(message = MessageKey.FIELD_REQUIRED) String content, String searchProvider) {
}
