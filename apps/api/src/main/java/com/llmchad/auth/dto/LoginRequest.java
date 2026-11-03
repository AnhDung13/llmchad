package com.llmchad.auth.dto;

import com.llmchad.common.MessageKey;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = MessageKey.FIELD_REQUIRED) @Email(message = MessageKey.EMAIL_INVALID) String email,
        @NotBlank(message = MessageKey.FIELD_REQUIRED) String password) {
}
