package com.llmchad.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {
}
