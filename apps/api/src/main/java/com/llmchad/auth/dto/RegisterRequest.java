package com.llmchad.auth.dto;

import com.llmchad.common.MessageKey;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
                @NotBlank(message = MessageKey.FIELD_REQUIRED) @Email(message = MessageKey.EMAIL_INVALID) String email,
                @NotBlank(message = MessageKey.FIELD_REQUIRED) @Size(min = 8, message = MessageKey.PASSWORD_TOO_SHORT) String password,
                @NotBlank(message = MessageKey.FIELD_REQUIRED) @Size(max = 256, message = MessageKey.DISPLAY_NAME_TOO_LONG) String displayName) {
}
