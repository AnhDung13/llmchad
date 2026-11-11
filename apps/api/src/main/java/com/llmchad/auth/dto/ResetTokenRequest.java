package com.llmchad.auth.dto;

import com.llmchad.common.MessageKey;

import jakarta.validation.constraints.NotBlank;

/** Chỉ hỏi token còn dùng được không (trang đặt lại mật khẩu gọi khi vừa mở), không dùng mất token */
public record ResetTokenRequest(@NotBlank(message = MessageKey.FIELD_REQUIRED) String token) {
}
