package com.llmchad.auth.dto;

import com.llmchad.common.MessageKey;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = MessageKey.FIELD_REQUIRED) String refreshToken) {
}
