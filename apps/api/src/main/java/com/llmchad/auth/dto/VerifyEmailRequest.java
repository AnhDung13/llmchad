package com.llmchad.auth.dto;

import com.llmchad.common.MessageKey;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank(message = MessageKey.FIELD_REQUIRED) @Email(message = MessageKey.EMAIL_INVALID) String email,
        // Mật khẩu vừa nhập ở form đăng ký: phải khớp bản đăng ký đang chờ (chống bị đăng ký đè, xem AuthService)
        @NotBlank(message = MessageKey.FIELD_REQUIRED) String password,
        @NotBlank(message = MessageKey.FIELD_REQUIRED) @Size(min = 6, max = 6, message = MessageKey.INVALID_VERIFY_CODE) String code) {
}
