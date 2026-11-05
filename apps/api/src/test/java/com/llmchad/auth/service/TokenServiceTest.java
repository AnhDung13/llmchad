package com.llmchad.auth.service;

import static com.llmchad.support.TestAssertions.assertAppException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.llmchad.auth.model.User;
import com.llmchad.common.MessageKey;
import com.llmchad.config.SecurityConfig;

/**
 * JwtEncoder/JwtDecoder là thật (lấy từ SecurityConfig) để kiểm tra token ký và giải mã được.
 * Redis là mock nên không cần Redis đang chạy.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    // Base64 của "test-secret-test-secret-test-secret!" (36 byte, đủ cho HS256)
    private static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQh";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private TokenService tokenService;
    private JwtDecoder jwtDecoder;

    private final User user = new User(UUID.randomUUID(), "an@example.com", "An", Instant.now());

    @BeforeEach
    void setUp() {
        SecurityConfig config = new SecurityConfig();
        SecretKey key = config.jwtSecretKey(SECRET);
        tokenService = new TokenService(config.jwtEncoder(key), ACCESS_TTL, REFRESH_TTL, redis);
        jwtDecoder = config.jwtDecoder(key);
    }

    @Test
    void getAccessTtlSeconds_returnsConfiguredTtl() {
        assertThat(tokenService.getAccessTtlSeconds()).isEqualTo(900L);
    }

    @Test
    void createAccessToken_isSignedAndContainsUserClaims() {
        String token = tokenService.createAccessToken(user);

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
        assertThat(jwt.getClaimAsString("name")).isEqualTo("An");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(ACCESS_TTL);
    }

    @Test
    void createRefreshToken_storesHashedKeyWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        String token = tokenService.createRefreshToken(user.id());

        // 32 byte random → 43 ký tự Base64 URL-safe, không padding
        assertThat(token).matches("[A-Za-z0-9_-]{43}");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), eq(user.id().toString()), eq(REFRESH_TTL));
        // Chỉ lưu hash của token, không lưu token gốc
        assertThat(keyCaptor.getValue()).startsWith("refresh_token:").doesNotContain(token);
    }

    @Test
    void createRefreshToken_generatesDifferentTokensEachTime() {
        when(redis.opsForValue()).thenReturn(valueOps);

        assertThat(tokenService.createRefreshToken(user.id()))
                .isNotEqualTo(tokenService.createRefreshToken(user.id()));
    }

    @Test
    void consumeRefreshToken_validToken_returnsUserIdUsingSameKey() {
        when(redis.opsForValue()).thenReturn(valueOps);
        String token = tokenService.createRefreshToken(user.id());
        String storedKey = capturedStoredKey();
        when(valueOps.getAndDelete(storedKey)).thenReturn(user.id().toString());

        assertThat(tokenService.consumeRefreshToken(token)).isEqualTo(user.id());
    }

    @Test
    void consumeRefreshToken_unknownOrUsedToken_throwsUnauthorized() {
        when(redis.opsForValue()).thenReturn(valueOps);
        // getAndDelete trả null (mặc định của mock) = token không tồn tại / đã dùng rồi

        assertAppException(() -> tokenService.consumeRefreshToken("unknown"),
                HttpStatus.UNAUTHORIZED, MessageKey.INVALID_REFRESH_TOKEN);
    }

    @Test
    void revokeRefreshToken_deletesSameKeyThatWasStored() {
        when(redis.opsForValue()).thenReturn(valueOps);
        String token = tokenService.createRefreshToken(user.id());
        String storedKey = capturedStoredKey();

        tokenService.revokeRefreshToken(token);

        verify(redis).delete(storedKey);
    }

    private String capturedStoredKey() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), eq(user.id().toString()), eq(REFRESH_TTL));
        return keyCaptor.getValue();
    }
}
