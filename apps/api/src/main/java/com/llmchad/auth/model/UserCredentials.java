package com.llmchad.auth.model;

public record UserCredentials(User user, String passwordHash) {
}
