package com.llmchad.chat.dto;

import com.llmchad.common.MessageKey;

import jakarta.validation.constraints.NotBlank;

public record MessageRequest(@NotBlank(message = MessageKey.FIELD_REQUIRED) String content) {

}
