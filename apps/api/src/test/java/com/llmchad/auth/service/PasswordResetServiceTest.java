package com.llmchad.auth.service;

import static com.llmchad.support.TestAssertions.assertAppException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.llmchad.auth.dto.ResetLinkSentResponse;
import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;
import com.llmchad.auth.repository.UserRepository;
import com.llmchad.common.EmailService;
import com.llmchad.common.HashUtils;
import com.llmchad.common.MessageKey;
import com.llmchad.exception.AppException;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String FRONTEND_URL = "https://llmchad.site";
    private static final String COOLDOWN_KEY = "forgot_password:an@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private EmailService emailService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private PasswordResetService service;
    private final User user = new User(UUID.randomUUID(), "an@example.com", "An", Instant.now());

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, passwordEncoder, tokenService, emailService, redis,
                FRONTEND_URL);
    }

    private void cooldownFree() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(true);
    }

    /** Link gửi trong mail; kiểm tra luôn là ghép từ config */
    private String mailedLink() {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetLink(eq("an@example.com"), link.capture(),
                eq(PasswordResetService.TOKEN_TTL));
        assertThat(link.getValue()).startsWith(FRONTEND_URL + "/reset-password?token=");
        return link.getValue();
    }

    private static String tokenOf(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    // ---------- requestReset ----------

    @Test
    void requestReset_existingUser_storesOnlyHashAndMailsLink() {
        cooldownFree();
        when(userRepository.findCredentialsByEmail("an@example.com"))
                .thenReturn(Optional.of(new UserCredentials(user, "hashed")));

        // Email có khoảng trắng và chữ hoa vẫn được chuẩn hoá
        ResetLinkSentResponse res = service.requestReset("  An@Example.com ");

        assertThat(res).isEqualTo(new ResetLinkSentResponse(900, 60));
        String token = tokenOf(mailedLink());
        assertThat(token).matches("[0-9A-Za-z]{32}");
        String hash = HashUtils.sha256(token);
        verify(valueOps).set("reset_token:" + hash, user.id().toString(), PasswordResetService.TOKEN_TTL);
        verify(valueOps).set("reset_token_user:" + user.id(), hash, PasswordResetService.TOKEN_TTL);
    }

    @Test
    void requestReset_oauthOnlyUserWithoutPassword_stillGetsLink() {
        cooldownFree();
        when(userRepository.findCredentialsByEmail("an@example.com"))
                .thenReturn(Optional.of(new UserCredentials(user, null)));

        service.requestReset("an@example.com");

        mailedLink();
    }

    @Test
    void requestReset_newLink_invalidatesPreviousLink() {
        cooldownFree();
        when(userRepository.findCredentialsByEmail("an@example.com"))
                .thenReturn(Optional.of(new UserCredentials(user, "hashed")));
        when(valueOps.get("reset_token_user:" + user.id())).thenReturn("old-hash");

        service.requestReset("an@example.com");

        verify(redis).delete("reset_token:old-hash");
    }

    @Test
    void requestReset_unknownEmail_respondsTheSameWithoutMail() {
        cooldownFree();
        when(userRepository.findCredentialsByEmail("an@example.com")).thenReturn(Optional.empty());

        assertThat(service.requestReset("an@example.com")).isEqualTo(new ResetLinkSentResponse(900, 60));
        verify(emailService, never()).sendPasswordResetLink(anyString(), anyString(), any());
    }

    @Test
    void requestReset_duringCooldown_throws429WithoutMail() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(false);

        assertAppException(() -> service.requestReset("an@example.com"),
                HttpStatus.TOO_MANY_REQUESTS, MessageKey.TOO_MANY_REQUESTS);
        verify(userRepository, never()).findCredentialsByEmail(anyString());
    }

    @Test
    void requestReset_mailFails_releasesCooldown() {
        cooldownFree();
        when(userRepository.findCredentialsByEmail("an@example.com"))
                .thenReturn(Optional.of(new UserCredentials(user, "hashed")));
        doThrow(new AppException(HttpStatus.BAD_GATEWAY, MessageKey.SEND_MAIL_FAILED))
                .when(emailService).sendPasswordResetLink(anyString(), anyString(), any());

        assertAppException(() -> service.requestReset("an@example.com"),
                HttpStatus.BAD_GATEWAY, MessageKey.SEND_MAIL_FAILED);
        verify(redis).delete(COOLDOWN_KEY);
    }

    // ---------- validateToken ----------

    @Test
    void validateToken_existing_passesWithoutConsuming() {
        when(redis.hasKey("reset_token:" + HashUtils.sha256("tok"))).thenReturn(true);

        service.validateToken("tok");

        verify(redis, never()).delete(anyString());
    }

    @Test
    void validateToken_unknown_throws410() {
        assertAppException(() -> service.validateToken("tok"), HttpStatus.GONE, MessageKey.RESET_TOKEN_INVALID);
    }

    // ---------- resetPassword ----------

    @Test
    void resetPassword_validToken_updatesPasswordAndLogsOutEverywhere() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("reset_token:" + HashUtils.sha256("tok"))).thenReturn(user.id().toString());
        when(passwordEncoder.encode("newpassword123")).thenReturn("new-hash");
        when(userRepository.updatePassword(user.id(), "new-hash")).thenReturn(true);

        service.resetPassword("tok", "newpassword123");

        verify(redis).delete("reset_token_user:" + user.id());
        verify(tokenService).revokeAllRefreshTokens(user.id());
    }

    @Test
    void resetPassword_usedOrExpiredToken_throws410WithoutTouchingPassword() {
        when(redis.opsForValue()).thenReturn(valueOps);
        // getAndDelete trả null (mặc định của mock) = token không tồn tại / đã dùng rồi

        assertAppException(() -> service.resetPassword("tok", "newpassword123"),
                HttpStatus.GONE, MessageKey.RESET_TOKEN_INVALID);
        verify(userRepository, never()).updatePassword(any(), anyString());
        verify(tokenService, never()).revokeAllRefreshTokens(any());
    }

    @Test
    void resetPassword_userDeleted_throws410() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("reset_token:" + HashUtils.sha256("tok"))).thenReturn(user.id().toString());
        when(passwordEncoder.encode("newpassword123")).thenReturn("new-hash");
        when(userRepository.updatePassword(user.id(), "new-hash")).thenReturn(false);

        assertAppException(() -> service.resetPassword("tok", "newpassword123"),
                HttpStatus.GONE, MessageKey.RESET_TOKEN_INVALID);
        verify(tokenService, never()).revokeAllRefreshTokens(any());
    }
}
