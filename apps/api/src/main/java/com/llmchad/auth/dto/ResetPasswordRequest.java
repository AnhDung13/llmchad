package com.llmchad.auth.dto;

import com.llmchad.common.MessageKey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** token: lấy từ link trong mail (/reset-password?token=...). newPassword: cùng luật với mật khẩu lúc đăng ký */
public record ResetPasswordRequest(
        @NotBlank(message = MessageKey.FIELD_REQUIRED) String token,
        @NotBlank(message = MessageKey.FIELD_REQUIRED) @Size(min = 8, message = MessageKey.PASSWORD_TOO_SHORT) String newPassword) {
}
