package com.llmchad.auth.model;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, String email, String displayName, Instant createdAt) {
}
