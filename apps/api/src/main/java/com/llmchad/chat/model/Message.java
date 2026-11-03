package com.llmchad.chat.model;

import java.time.Instant;
import java.util.UUID;

public record Message(UUID id, String role, String content, String reasoning, Instant createdAt) {
}