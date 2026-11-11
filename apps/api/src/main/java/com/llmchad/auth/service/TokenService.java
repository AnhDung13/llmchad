package com.llmchad.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
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

import com.llmchad.auth.model.User;
import com.llmchad.common.HashUtils;
import com.llmchad.common.MessageKey;
import com.llmchad.exception.AppException;

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

    // refresh_token:<sha256(token)> → userId
    // refresh_tokens_of:<userId>     → Set các sha256 đang còn hiệu lực của user, để đăng xuất mọi thiết bị
    private static final String TOKEN_KEY_PREFIX = "refresh_token:";
    private static final String USER_TOKENS_KEY_PREFIX = "refresh_tokens_of:";

    public String createRefreshToken(UUID userId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String hash = HashUtils.sha256(token);
        redis.opsForValue().set(TOKEN_KEY_PREFIX + hash, userId.toString(), refreshTtl);
        String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;
        redis.opsForSet().add(userTokensKey, hash);
        // Set sống bằng token mới nhất; token trong Set có thể đã hết hạn trước, xoá thừa key không tồn tại không sao
        redis.expire(userTokensKey, refreshTtl);
        return token;
    }

    public UUID consumeRefreshToken(String token) {
        String hash = HashUtils.sha256(token);
        String userId = redis.opsForValue().getAndDelete(TOKEN_KEY_PREFIX + hash);
        if (userId == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, MessageKey.INVALID_REFRESH_TOKEN);
        }
        // Mỗi lần refresh sinh token mới: bỏ token cũ khỏi Set để Set không phình mãi
        redis.opsForSet().remove(USER_TOKENS_KEY_PREFIX + userId, hash);
        return UUID.fromString(userId);
    }

    public void revokeRefreshToken(String token) {
        String hash = HashUtils.sha256(token);
        String userId = redis.opsForValue().getAndDelete(TOKEN_KEY_PREFIX + hash);
        if (userId != null) {
            redis.opsForSet().remove(USER_TOKENS_KEY_PREFIX + userId, hash);
        }
    }

    /** Đăng xuất mọi thiết bị (vd sau khi đặt lại mật khẩu). Access token đang có vẫn sống tới khi hết hạn (15 phút). */
    public void revokeAllRefreshTokens(UUID userId) {
        String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;
        Set<String> hashes = redis.opsForSet().members(userTokensKey);
        if (hashes != null && !hashes.isEmpty()) {
            redis.delete(hashes.stream().map(hash -> TOKEN_KEY_PREFIX + hash).toList());
        }
        redis.delete(userTokensKey);
    }
}
