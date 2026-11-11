package com.llmchad.auth.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.llmchad.auth.dto.ResetLinkSentResponse;
import com.llmchad.auth.model.UserCredentials;
import com.llmchad.auth.repository.UserRepository;
import com.llmchad.common.EmailService;
import com.llmchad.common.EmailUtils;
import com.llmchad.common.HashUtils;
import com.llmchad.common.MessageKey;
import com.llmchad.common.RandomCode;
import com.llmchad.exception.AppException;

/**
 * Quên mật khẩu bằng link trong mail (xem docs/api-contract.md → Bước 10).
 *
 * Redis (chỉ lưu hash của token, không lưu token gốc):
 *   reset_token:<sha256(token)>  → userId   (link hợp lệ)
 *   reset_token_user:<userId>    → sha256    (token mới nhất của user, để huỷ link cũ khi gửi link mới)
 * Token 32 ký tự ngẫu nhiên không đoán được nên không cần đếm số lần thử như mã 6 ký tự.
 */
@Service
public class PasswordResetService {
    static final String TOKEN_KEY_PREFIX = "reset_token:";
    static final String USER_TOKEN_KEY_PREFIX = "reset_token_user:";
    static final String COOLDOWN_KEY_PREFIX = "forgot_password:";
    static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    static final Duration COOLDOWN = Duration.ofSeconds(60);
    static final int TOKEN_LENGTH = 32;
    static final String RESET_PATH = "/reset-password?token=";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final StringRedisTemplate redis;
    private final String frontendUrl;

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            TokenService tokenService, EmailService emailService, StringRedisTemplate redis,
            // Link trong mail lấy từ config, KHÔNG lấy từ Host của request: kẻ gian gửi Host: evil.com thì
            // mail thật của mình sẽ chứa link trỏ về evil.com và token rơi vào tay họ
            @Value("${app.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.redis = redis;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Email không tồn tại vẫn trả như thành công: không cho người ngoài dò email nào đã đăng ký.
     * Gửi lại link = gọi lại hàm này (có cooldown); link cũ bị huỷ, chỉ link mới nhất dùng được.
     */
    public ResetLinkSentResponse requestReset(String rawEmail) {
        String email = EmailUtils.normalize(rawEmail);
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(cooldownKey, "1", COOLDOWN))) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, MessageKey.TOO_MANY_REQUESTS);
        }

        Optional<UserCredentials> credentials = userRepository.findCredentialsByEmail(email);
        // Tài khoản chỉ đăng nhập bằng Google/GitHub (chưa có mật khẩu) vẫn được đặt mật khẩu: họ chứng minh
        // sở hữu email bằng cách bấm link
        if (credentials.isPresent()) {
            String token = createToken(credentials.get().user().id());
            try {
                emailService.sendPasswordResetLink(email, frontendUrl + RESET_PATH + token, TOKEN_TTL);
            } catch (AppException e) {
                // Gửi mail lỗi: cho thử lại ngay, không bắt chờ hết cooldown
                redis.delete(cooldownKey);
                throw e;
            }
        }
        return new ResetLinkSentResponse(TOKEN_TTL.toSeconds(), COOLDOWN.toSeconds());
    }

    /** Trang đặt lại mật khẩu gọi khi vừa mở để báo sớm link hỏng. Chỉ đọc, không xoá token. */
    public void validateToken(String token) {
        if (!Boolean.TRUE.equals(redis.hasKey(tokenKey(token)))) {
            throw new AppException(HttpStatus.GONE, MessageKey.RESET_TOKEN_INVALID);
        }
    }

    public void resetPassword(String token, String newPassword) {
        // getAndDelete: lấy và xoá trong một lệnh → hai request cùng token thì chỉ một cái thành công
        String userIdValue = redis.opsForValue().getAndDelete(tokenKey(token));
        if (userIdValue == null) {
            throw new AppException(HttpStatus.GONE, MessageKey.RESET_TOKEN_INVALID);
        }
        UUID userId = UUID.fromString(userIdValue);
        redis.delete(USER_TOKEN_KEY_PREFIX + userId);

        if (!userRepository.updatePassword(userId, passwordEncoder.encode(newPassword))) {
            // User đã bị xoá sau khi gửi link
            throw new AppException(HttpStatus.GONE, MessageKey.RESET_TOKEN_INVALID);
        }
        // Người quên/nghi lộ mật khẩu: đăng xuất mọi thiết bị đang đăng nhập
        tokenService.revokeAllRefreshTokens(userId);
    }

    private String createToken(UUID userId) {
        String userTokenKey = USER_TOKEN_KEY_PREFIX + userId;
        String previousHash = redis.opsForValue().get(userTokenKey);
        if (previousHash != null) {
            redis.delete(TOKEN_KEY_PREFIX + previousHash);
        }
        String token = RandomCode.generate(TOKEN_LENGTH);
        String hash = HashUtils.sha256(token);
        redis.opsForValue().set(TOKEN_KEY_PREFIX + hash, userId.toString(), TOKEN_TTL);
        redis.opsForValue().set(userTokenKey, hash, TOKEN_TTL);
        return token;
    }

    private static String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + HashUtils.sha256(token);
    }
}
