package com.llmchat.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.llmchat.auth.model.User;
import com.llmchat.common.MessageKey;
import com.llmchat.exception.AppException;

@Service
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redis;

    public TokenService(JwtEncoder jwtEncoder,
            @Value("${app.jwt.access-ttl}") Duration accessTtl,
            @Value("${app.jwt.refresh-ttl}") Duration refreshTtl, StringRedisTemplate redis) {
        this.jwtEncoder = jwtEncoder;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.redis = redis;
    }

    public long getAccessTtlSeconds() {
        return accessTtl.getSeconds();
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(user.id().toString()).claim("name", user.displayName())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl)).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String createRefreshToken(UUID userId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        redis.opsForValue().set(createRedisKey(token), userId.toString(), refreshTtl);
        return token;
    }

    public UUID consumeRefreshToken(String token) {
        String userId = redis.opsForValue().getAndDelete(createRedisKey(token));
        if (userId == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, MessageKey.INVALID_REFRESH_TOKEN);
        }
        return UUID.fromString(userId);
    }

    public void revokeRefreshToken(String token) {
        redis.delete(createRedisKey(token));
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String createRedisKey(String token) {
        return "refresh_token:" + hashToken(token);
    }
}
