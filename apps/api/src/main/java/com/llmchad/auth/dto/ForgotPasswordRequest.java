package com.llmchad.auth.dto;

import com.llmchad.common.MessageKey;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = MessageKey.FIELD_REQUIRED) @Email(message = MessageKey.EMAIL_INVALID) String email) {
}
