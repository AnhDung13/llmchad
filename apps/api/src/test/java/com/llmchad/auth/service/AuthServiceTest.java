package com.llmchad.auth.service;

import static com.llmchad.support.TestAssertions.assertAppException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.llmchad.auth.dto.LoginRequest;
import com.llmchad.auth.dto.LoginResponse;
import com.llmchad.auth.dto.RefreshRequest;
import com.llmchad.auth.dto.RegisterRequest;
import com.llmchad.auth.dto.ResendVerificationRequest;
import com.llmchad.auth.dto.TokenResponse;
import com.llmchad.auth.dto.VerificationCodeResponse;
import com.llmchad.auth.dto.VerifyEmailRequest;
import com.llmchad.auth.entity.UserEntity;
import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;
import com.llmchad.auth.oauth2.OAuthLoginService;
import com.llmchad.auth.repository.UserRepository;
import com.llmchad.common.EmailService;
import com.llmchad.common.HashUtils;
import com.llmchad.common.MessageKey;
import com.llmchad.exception.AppException;
import com.llmchad.support.TestEntities;

/** Unit test thuần Mockito: không khởi động Spring, mọi dependency đều là mock. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private EmailService emailService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private OAuthLoginService oauthLoginService;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private AuthService authService;

    private final User user = new User(UUID.randomUUID(), "an@example.com", "An", Instant.now());

    private static final String PENDING_KEY = "pending_register:an@example.com";
    private static final String COOLDOWN_KEY = "resend_verification:an@example.com";

    /** Bản đăng ký đang chờ trong Redis, mã đúng là "Ab3dE9", mã còn hạn */
    private Map<Object, Object> pending(String attempts) {
        return pending(attempts, Instant.now().plusSeconds(180));
    }

    private Map<Object, Object> pending(String attempts, Instant codeExpiresAt) {
        return Map.of("passwordHash", "hashed", "displayName", "An",
                "codeHash", HashUtils.sha256("Ab3dE9"), "attempts", attempts,
                "codeExpiresAt", String.valueOf(codeExpiresAt.toEpochMilli()));
    }

    private void stubTokens() {
        when(tokenService.createAccessToken(user)).thenReturn("access");
        when(tokenService.createRefreshToken(user.id())).thenReturn("refresh");
        when(tokenService.getAccessTtlSeconds()).thenReturn(900L);
    }

    // ---------- register ----------

    @Test
    void register_emailAlreadyExists_throwsConflict() {
        when(userRepository.existsByEmail("an@example.com")).thenReturn(true);

        assertAppException(() -> authService.register(new RegisterRequest("an@example.com", "password123", "An")),
                HttpStatus.CONFLICT, MessageKey.EMAIL_ALREADY_EXISTS);
        verify(emailService, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void register_newEmail_storesPendingAndMailsCode_withoutCreatingUser() {
        when(userRepository.existsByEmail("an@example.com")).thenReturn(false);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        // Email có khoảng trắng và chữ hoa vẫn được chuẩn hoá
        VerificationCodeResponse res = authService.register(new RegisterRequest("  An@Example.com ", "password123", "An"));

        assertThat(res).isEqualTo(new VerificationCodeResponse(180, 60));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(eq("an@example.com"), code.capture(), any());
        assertThat(code.getValue()).matches("[0-9A-Za-z]{6}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Object, Object>> saved = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq(PENDING_KEY), saved.capture());
        assertThat(saved.getValue()).containsEntry("passwordHash", "hashed")
                .containsEntry("displayName", "An")
                .containsEntry("codeHash", HashUtils.sha256(code.getValue()))
                .containsEntry("attempts", "0");
        assertThat(Long.parseLong((String) saved.getValue().get("codeExpiresAt")))
                .isBetween(Instant.now().plusSeconds(170).toEpochMilli(), Instant.now().plusSeconds(181).toEpochMilli());
        // Mã sống 3 phút, bản đăng ký sống 15 phút
        verify(emailService).sendVerificationCode(anyString(), anyString(), eq(Duration.ofMinutes(3)));
        verify(redis).expire(PENDING_KEY, Duration.ofMinutes(15));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_withinCooldown_throwsTooManyRequests() {
        when(userRepository.existsByEmail("an@example.com")).thenReturn(false);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(false);

        assertAppException(() -> authService.register(new RegisterRequest("an@example.com", "password123", "An")),
                HttpStatus.TOO_MANY_REQUESTS, MessageKey.TOO_MANY_REQUESTS);
        verify(emailService, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void register_mailFails_releasesCooldownSoUserCanRetry() {
        when(userRepository.existsByEmail("an@example.com")).thenReturn(false);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        doThrow(new AppException(HttpStatus.BAD_GATEWAY, MessageKey.SEND_MAIL_FAILED))
                .when(emailService).sendVerificationCode(anyString(), anyString(), any());

        assertAppException(() -> authService.register(new RegisterRequest("an@example.com", "password123", "An")),
                HttpStatus.BAD_GATEWAY, MessageKey.SEND_MAIL_FAILED);
        verify(redis).delete(COOLDOWN_KEY);
    }

    // ---------- verifyEmail ----------

    @Test
    void verifyEmail_noPending_throwsExpired() {
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(PENDING_KEY)).thenReturn(Map.of());

        assertAppException(() -> authService.verifyEmail(new VerifyEmailRequest("an@example.com", "password123", "Ab3dE9")),
                HttpStatus.NOT_FOUND, MessageKey.VERIFICATION_EXPIRED);
    }

    @Test
    void verifyEmail_wrongCode_countsAttempt() {
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(PENDING_KEY)).thenReturn(pending("0"));
        when(hashOps.increment(PENDING_KEY, "attempts", 1)).thenReturn(1L);

        // Mã phân biệt hoa thường: "ab3de9" khác "Ab3dE9"
        assertAppException(() -> authService.verifyEmail(new VerifyEmailRequest("an@example.com", "password123", "ab3de9")),
                HttpStatus.UNPROCESSABLE_CONTENT, MessageKey.INVALID_VERIFY_CODE);
        verify(redis, never()).delete(anyString());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void verifyEmail_codeOlderThanThreeMinutes_throwsGoneWithoutCountingAttempt() {
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(PENDING_KEY)).thenReturn(pending("0", Instant.now().minusSeconds(1)));

        assertAppException(() -> authService.verifyEmail(new VerifyEmailRequest("an@example.com", "password123", "Ab3dE9")),
                HttpStatus.GONE, MessageKey.VERIFY_CODE_EXPIRED);
        verify(hashOps, never()).increment(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(redis, never()).delete(anyString());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void verifyEmail_rightCodeButPendingOverwrittenWithOtherPassword_doesNotCreateUser() {
        // Người khác đã đăng ký đè email này bằng mật khẩu của họ: chủ email nhập đúng mã nhưng mật khẩu không khớp
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(PENDING_KEY)).thenReturn(pending("0"));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(false);

        assertAppException(() -> authService.verifyEmail(new VerifyEmailRequest("an@example.com", "password123", "Ab3dE9")),
                HttpStatus.NOT_FOUND, MessageKey.VERIFICATION_EXPIRED);
        verify(redis).delete(PENDING_KEY);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void verifyEmail_wrongCode_neverChecksPassword() {
        // Không có mã thì không dò được mật khẩu qua endpoint này
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(PENDING_KEY)).thenReturn(pending("0"));
        when(hashOps.increment(PENDING_KEY, "attempts", 1)).thenReturn(1L);

        assertAppException(() -> authService.verifyEmail(new VerifyEmailRequest("an@example.com", "guess", "zzzzzz")),
                HttpStatus.UNPROCESSABLE_CONTENT, MessageKey.INVALID_VERIFY_CODE);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void verifyEmail_partialHashFromResendRace_treatedAsExpired() {
        // resend ghi vào đúng lúc key vừa hết hạn → hash chỉ còn field của mã, thiếu passwordHash/displayName
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(PENDING_KEY)).thenReturn(Map.of("codeHash", HashUtils.sha256("Ab3dE9"), "attempts", "0",
                "codeExpiresAt", String.valueOf(Instant.now().plusSeconds(180).toEpochMilli())));

        assertAppException(() -> authService.verifyEmail(new VerifyEmailRequest("an@example.com", "password123", "Ab3dE9")),
                HttpStatus.NOT_FOUND, MessageKey.VERIFICATION_EXPIRED);
        verify(redis).delete(PENDING_KEY);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void verifyEmail_fifthWrongCode_deletesPending() {
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(PENDING_KEY)).thenReturn(pending("4"));
        when(hashOps.increment(PENDING_KEY, "attempts", 1)).thenReturn(5L);

        assertAppException(() -> authService.verifyEmail(new VerifyEmailRequest("an@example.com", "password123", "zzzzzz")),
                HttpStatus.TOO_MANY_REQUESTS, MessageKey.VERIFY_ATTEMPTS_EXCEEDED);
        verify(redis).delete(PENDING_KEY);
    }

    @Test
    void verifyEmail_correctCode_createsUserAndLogsIn() {
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(PENDING_KEY)).thenReturn(pending("2"));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(redis.delete(PENDING_KEY)).thenReturn(true);
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenReturn(TestEntities.savedUser(user, "hashed"));
        stubTokens();

        LoginResponse res = authService.verifyEmail(new VerifyEmailRequest("An@example.com", "password123", "Ab3dE9"));

        assertThat(res.accessToken()).isEqualTo("access");
        assertThat(res.user()).isEqualTo(user);
        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue())
                .extracting(UserEntity::getEmail, UserEntity::getPasswordHash, UserEntity::getDisplayName)
                .containsExactly("an@example.com", "hashed", "An");
    }

    @Test
    void verifyEmail_alreadyConsumedByConcurrentRequest_throwsExpired() {
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(PENDING_KEY)).thenReturn(pending("0"));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(redis.delete(PENDING_KEY)).thenReturn(false);

        assertAppException(() -> authService.verifyEmail(new VerifyEmailRequest("an@example.com", "password123", "Ab3dE9")),
                HttpStatus.NOT_FOUND, MessageKey.VERIFICATION_EXPIRED);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void verifyEmail_emailTakenMeanwhile_throwsConflict() {
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(PENDING_KEY)).thenReturn(pending("0"));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(redis.delete(PENDING_KEY)).thenReturn(true);
        doThrow(new DuplicateKeyException("users_email_key"))
                .when(userRepository).saveAndFlush(any(UserEntity.class));

        assertAppException(() -> authService.verifyEmail(new VerifyEmailRequest("an@example.com", "password123", "Ab3dE9")),
                HttpStatus.CONFLICT, MessageKey.EMAIL_ALREADY_EXISTS);
    }

    // ---------- resendVerificationCode ----------

    @Test
    void resend_noPending_throwsExpired() {
        when(redis.hasKey(PENDING_KEY)).thenReturn(false);

        assertAppException(() -> authService.resendVerificationCode(new ResendVerificationRequest("an@example.com")),
                HttpStatus.NOT_FOUND, MessageKey.VERIFICATION_EXPIRED);
        verify(emailService, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void resend_pending_replacesCodeAndResetsAttempts() {
        when(redis.hasKey(PENDING_KEY)).thenReturn(true);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        when(redis.<Object, Object>opsForHash()).thenReturn(hashOps);

        VerificationCodeResponse res = authService.resendVerificationCode(new ResendVerificationRequest("an@example.com"));

        assertThat(res).isEqualTo(new VerificationCodeResponse(180, 60));
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(eq("an@example.com"), code.capture(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Object, Object>> saved = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq(PENDING_KEY), saved.capture());
        assertThat(saved.getValue()).containsEntry("codeHash", HashUtils.sha256(code.getValue()))
                .containsEntry("attempts", "0")
                .containsKey("codeExpiresAt");
        verify(redis).expire(PENDING_KEY, Duration.ofMinutes(15));
    }

    @Test
    void resend_mailFails_keepsOldCodeAndReleasesCooldown() {
        when(redis.hasKey(PENDING_KEY)).thenReturn(true);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        doThrow(new AppException(HttpStatus.BAD_GATEWAY, MessageKey.SEND_MAIL_FAILED))
                .when(emailService).sendVerificationCode(anyString(), anyString(), any());

        assertAppException(() -> authService.resendVerificationCode(new ResendVerificationRequest("an@example.com")),
                HttpStatus.BAD_GATEWAY, MessageKey.SEND_MAIL_FAILED);
        // Mã cũ người dùng đang cầm vẫn còn hiệu lực
        verify(hashOps, never()).putAll(anyString(), anyMap());
        verify(redis).delete(COOLDOWN_KEY);
    }

    @Test
    void resend_withinCooldown_throwsTooManyRequests() {
        when(redis.hasKey(PENDING_KEY)).thenReturn(true);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class))).thenReturn(false);

        assertAppException(() -> authService.resendVerificationCode(new ResendVerificationRequest("an@example.com")),
                HttpStatus.TOO_MANY_REQUESTS, MessageKey.TOO_MANY_REQUESTS);
        verify(hashOps, never()).putAll(anyString(), anyMap());
    }

    // ---------- login ----------

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findCredentialsByEmail("an@example.com")).thenReturn(Optional.empty());

        assertAppException(() -> authService.login(new LoginRequest("an@example.com", "password123")),
                HttpStatus.UNAUTHORIZED, MessageKey.INVALID_CREDENTIALS);
        verify(tokenService, never()).createAccessToken(any());
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        when(userRepository.findCredentialsByEmail("an@example.com"))
                .thenReturn(Optional.of(new UserCredentials(user, "hashed")));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertAppException(() -> authService.login(new LoginRequest("an@example.com", "wrong-password")),
                HttpStatus.UNAUTHORIZED, MessageKey.INVALID_CREDENTIALS);
        verify(tokenService, never()).createAccessToken(any());
        verify(tokenService, never()).createRefreshToken(any());
    }

    @Test
    void login_oauthOnlyUserWithoutPassword_throwsUnauthorized() {
        when(userRepository.findCredentialsByEmail("an@example.com"))
                .thenReturn(Optional.of(new UserCredentials(user, null)));

        assertAppException(() -> authService.login(new LoginRequest("an@example.com", "password123")),
                HttpStatus.UNAUTHORIZED, MessageKey.INVALID_CREDENTIALS);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_correctPassword_returnsTokensAndUser() {
        when(userRepository.findCredentialsByEmail("an@example.com"))
                .thenReturn(Optional.of(new UserCredentials(user, "hashed")));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(tokenService.createAccessToken(user)).thenReturn("access");
        when(tokenService.createRefreshToken(user.id())).thenReturn("refresh");
        when(tokenService.getAccessTtlSeconds()).thenReturn(900L);

        LoginResponse res = authService.login(new LoginRequest("an@example.com", "password123"));

        assertThat(res.accessToken()).isEqualTo("access");
        assertThat(res.refreshToken()).isEqualTo("refresh");
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.expiresIn()).isEqualTo(900L);
        assertThat(res.user()).isEqualTo(user);
    }

    // ---------- refresh / logout ----------

    @Test
    void refresh_consumesOldTokenAndIssuesNewPair() {
        when(tokenService.consumeRefreshToken("old-refresh")).thenReturn(user.id());
        when(userService.getUserById(user.id())).thenReturn(user);
        when(tokenService.createAccessToken(user)).thenReturn("new-access");
        when(tokenService.createRefreshToken(user.id())).thenReturn("new-refresh");
        when(tokenService.getAccessTtlSeconds()).thenReturn(900L);

        TokenResponse res = authService.refresh(new RefreshRequest("old-refresh"));

        assertThat(res).isEqualTo(new TokenResponse("new-access", "Bearer", 900L, "new-refresh"));
    }

    @Test
    void logout_revokesRefreshToken() {
        authService.logout("refresh");

        verify(tokenService).revokeRefreshToken("refresh");
    }

    // ---------- loginWithOAuthCode ----------

    @Test
    void loginWithOAuthCode_validCodeAndNonce_returnsTokens() {
        when(oauthLoginService.consumeLoginCode("one-time-code", "nonce")).thenReturn(Optional.of(user.id()));
        when(userService.getUserById(user.id())).thenReturn(user);
        stubTokens();

        LoginResponse res = authService.loginWithOAuthCode("one-time-code", "nonce");

        assertThat(res.accessToken()).isEqualTo("access");
        assertThat(res.user()).isEqualTo(user);
    }

    @Test
    void loginWithOAuthCode_usedExpiredOrWrongBrowser_throwsUnauthorized() {
        when(oauthLoginService.consumeLoginCode("used", null)).thenReturn(Optional.empty());

        assertAppException(() -> authService.loginWithOAuthCode("used", null),
                HttpStatus.UNAUTHORIZED, MessageKey.INVALID_OAUTH_CODE);
        verify(tokenService, never()).createAccessToken(any());
    }
}
