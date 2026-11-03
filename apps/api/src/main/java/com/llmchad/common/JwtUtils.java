package com.llmchad.common;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtUtils {
    private JwtUtils() {
    }

    public static UUID subjectToUUID(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
