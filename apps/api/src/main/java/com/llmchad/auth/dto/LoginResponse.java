package com.llmchad.auth.dto;

import com.llmchad.auth.model.User;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, String refreshToken, User user) {
}
