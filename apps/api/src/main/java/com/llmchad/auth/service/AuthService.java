package com.llmchad.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.llmchad.auth.dto.LoginRequest;
import com.llmchad.auth.dto.LoginResponse;
import com.llmchad.auth.dto.RefreshRequest;
import com.llmchad.auth.dto.RegisterRequest;
import com.llmchad.auth.dto.ResendVerificationRequest;
import com.llmchad.auth.dto.TokenResponse;
import com.llmchad.auth.dto.VerificationCodeResponse;
import com.llmchad.auth.dto.VerifyEmailRequest;
import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;
import com.llmchad.auth.oauth2.OAuthLoginService;
import com.llmchad.auth.repository.UserRepository;
import com.llmchad.common.EmailService;
import com.llmchad.common.EmailUtils;
import com.llmchad.common.HashUtils;
import com.llmchad.common.MessageKey;
import com.llmchad.common.RandomCode;
import com.llmchad.exception.AppException;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final StringRedisTemplate redis;
    private final OAuthLoginService oauthLoginService;

    public AuthService(UserRepository userRepository, UserService userService, PasswordEncoder passwordEncoder,
            TokenService tokenService, EmailService emailService, StringRedisTemplate redis,
            OAuthLoginService oauthLoginService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.redis = redis;
        this.oauthLoginService = oauthLoginService;
    }

    private static final String PENDING_KEY_PREFIX = "pending_register:";
    private static final Duration PENDING_TTL = Duration.ofMinutes(15);
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final String RESEND_COOLDOWN_KEY_PREFIX = "resend_verification:";
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int VERIFY_CODE_LENGTH = 6;

    public VerificationCodeResponse register(RegisterRequest payload) {
        String email = EmailUtils.normalize(payload.email());
        if (userRepository.existEmail(email)) {
            throw new AppException(HttpStatus.CONFLICT, MessageKey.EMAIL_ALREADY_EXISTS);
        }
        acquireResendCooldown(email);

        String pendingKey = PENDING_KEY_PREFIX + email;
        String code = RandomCode.generate(VERIFY_CODE_LENGTH);
        redis.delete(pendingKey);
        redis.opsForHash().putAll(pendingKey,
                Map.of("passwordHash", passwordEncoder.encode(payload.password()), "displayName",
                        payload.displayName(), "codeHash", HashUtils.sha256(code), "attempts", "0",
                        "codeExpiresAt", codeExpiresAt()));
        redis.expire(pendingKey, PENDING_TTL);
        sendCode(email, code);
        return codeSent();
    }

    public LoginResponse verifyEmail(VerifyEmailRequest payload) {
        String email = EmailUtils.normalize(payload.email());
        String pendingKey = PENDING_KEY_PREFIX + email;
        Map<Object, Object> pending = redis.opsForHash().entries(pendingKey);
        String passwordHash = (String) pending.get("passwordHash");
        String displayName = (String) pending.get("displayName");
        String codeHash = (String) pending.get("codeHash");
        String codeExpiresAt = (String) pending.get("codeExpiresAt");

        if (passwordHash == null || displayName == null || codeHash == null || codeExpiresAt == null) {
            redis.delete(pendingKey);
            throw new AppException(HttpStatus.NOT_FOUND, MessageKey.VERIFICATION_EXPIRED);
        }

        if (Instant.now().toEpochMilli() > Long.parseLong(codeExpiresAt)) {
            throw new AppException(HttpStatus.GONE, MessageKey.VERIFY_CODE_EXPIRED);
        }

        if (!MessageDigest.isEqual(HashUtils.sha256(payload.code()).getBytes(StandardCharsets.UTF_8),
                codeHash.getBytes(StandardCharsets.UTF_8))) {
            Long failedAttempts = redis.opsForHash().increment(pendingKey, "attempts", 1);
            if (failedAttempts != null && failedAttempts >= MAX_VERIFY_ATTEMPTS) {
                redis.delete(pendingKey);
                throw new AppException(HttpStatus.TOO_MANY_REQUESTS, MessageKey.VERIFY_ATTEMPTS_EXCEEDED);
            }
            throw new AppException(HttpStatus.UNPROCESSABLE_CONTENT, MessageKey.INVALID_VERIFY_CODE);
        }

        if (!passwordEncoder.matches(payload.password(), passwordHash)) {
            redis.delete(pendingKey);
            throw new AppException(HttpStatus.NOT_FOUND, MessageKey.VERIFICATION_EXPIRED);
        }

        if (!Boolean.TRUE.equals(redis.delete(pendingKey))) {
            throw new AppException(HttpStatus.NOT_FOUND, MessageKey.VERIFICATION_EXPIRED);
        }
        User user;
        try {
            user = userRepository.insertUser(email, passwordHash, displayName);
        } catch (DuplicateKeyException e) {
            throw new AppException(HttpStatus.CONFLICT, MessageKey.EMAIL_ALREADY_EXISTS);
        }
        return issueTokens(user);
    }

    public VerificationCodeResponse resendVerificationCode(ResendVerificationRequest payload) {
        String email = EmailUtils.normalize(payload.email());
        String pendingKey = PENDING_KEY_PREFIX + email;
        if (!Boolean.TRUE.equals(redis.hasKey(pendingKey))) {
            throw new AppException(HttpStatus.NOT_FOUND, MessageKey.VERIFICATION_EXPIRED);
        }
        acquireResendCooldown(email);

        String code = RandomCode.generate(VERIFY_CODE_LENGTH);
        sendCode(email, code);
        redis.opsForHash().putAll(pendingKey,
                Map.of("codeHash", HashUtils.sha256(code), "attempts", "0", "codeExpiresAt", codeExpiresAt()));
        redis.expire(pendingKey, PENDING_TTL);
        return codeSent();
    }

    public TokenResponse refresh(RefreshRequest payload) {
        User user = userService.getUserById(tokenService.consumeRefreshToken(payload.refreshToken()));
        String accessToken = tokenService.createAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user.id());
        return new TokenResponse(accessToken, "Bearer", tokenService.getAccessTtlSeconds(), refreshToken);
    }

    public LoginResponse login(LoginRequest payload) {
        UserCredentials userCredentials = userRepository.findCredentialsByEmail(EmailUtils.normalize(payload.email()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, MessageKey.INVALID_CREDENTIALS));

        if (userCredentials.passwordHash() == null
                || !passwordEncoder.matches(payload.password(), userCredentials.passwordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, MessageKey.INVALID_CREDENTIALS);
        }
        return issueTokens(userCredentials.user());
    }

    public void logout(String token) {
        tokenService.revokeRefreshToken(token);
    }

    private void sendCode(String email, String code) {
        try {
            emailService.sendVerificationCode(email, code, CODE_TTL);
        } catch (AppException e) {
            redis.delete(RESEND_COOLDOWN_KEY_PREFIX + email);
            throw e;
        }
    }

    private static String codeExpiresAt() {
        return String.valueOf(Instant.now().plus(CODE_TTL).toEpochMilli());
    }

    private static VerificationCodeResponse codeSent() {
        return new VerificationCodeResponse(CODE_TTL.toSeconds(), RESEND_COOLDOWN.toSeconds());
    }

    private void acquireResendCooldown(String email) {
        Boolean acquired = redis.opsForValue().setIfAbsent(RESEND_COOLDOWN_KEY_PREFIX + email, "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, MessageKey.TOO_MANY_REQUESTS);
        }
    }

    /**
     * Đổi mã dùng 1 lần (OAuthLoginSuccessHandler tạo, sống 1 phút) lấy token,
     * giống hệt /login
     */
    public LoginResponse loginWithOAuthCode(String code, String nonce) {
        UUID userId = oauthLoginService.consumeLoginCode(code, nonce)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, MessageKey.INVALID_OAUTH_CODE));
        return issueTokens(userService.getUserById(userId));
    }

    private LoginResponse issueTokens(User user) {
        String accessToken = tokenService.createAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user.id());
        return new LoginResponse(accessToken, "Bearer", tokenService.getAccessTtlSeconds(), refreshToken, user);
    }
}
