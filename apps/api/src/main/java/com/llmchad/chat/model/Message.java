package com.llmchad.chat.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record Message(UUID id, String role, String content, String reasoning, @JsonRawValue String sources,
                Instant createdAt) {
}
