package com.llmchad.auth.dto;

import com.llmchad.common.MessageKey;

import jakarta.validation.constraints.NotBlank;

/**
 * Mã dùng 1 lần backend gắn vào /oauth/callback?code=... sau khi đăng nhập
 * Google/GitHub
 */
public record OAuthExchangeRequest(@NotBlank(message = MessageKey.FIELD_REQUIRED) String code) {
}
