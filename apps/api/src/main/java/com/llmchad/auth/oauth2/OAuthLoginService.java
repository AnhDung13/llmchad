package com.llmchad.auth.oauth2;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;
import com.llmchad.auth.repository.UserRepository;
import com.llmchad.common.EmailUtils;
import com.llmchad.common.HashUtils;
import com.llmchad.common.RandomCode;

@Service
public class OAuthLoginService {
    // Mã dùng 1 lần đưa về frontend qua URL (/oauth/callback?code=...). Dài vì nằm
    // trên URL, ai cũng đoán thử được.
    // AuthService (POST /auth/oauth/exchange) đọc lại theo đúng prefix này.
    private static final String LOGIN_CODE_KEY_PREFIX = "oauth_code:";
    // Cookie giữ nửa "nonce" của mã. Path hẹp: trình duyệt chỉ gửi nó kèm đúng request /api/auth/oauth/exchange
    public static final String LOGIN_NONCE_COOKIE = "oauth_login_nonce";
    public static final String LOGIN_NONCE_COOKIE_PATH = "/api/auth/oauth";
    public static final Duration LOGIN_CODE_TTL = Duration.ofMinutes(1);
    private static final int LOGIN_CODE_LENGTH = 32;
    // Khớp cột users.display_name VARCHAR(256)
    private static final int MAX_DISPLAY_NAME_LENGTH = 256;

    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final StringRedisTemplate redis;

    public OAuthLoginService(UserRepository userRepository, UserIdentityRepository identityRepository,
            StringRedisTemplate redis) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.redis = redis;
    }

    /**
     * Tìm user đã liên kết với tài khoản Google/GitHub này, hoặc liên kết/tạo mới
     * theo email.
     * {@code email} phải là email provider đã xác minh (handler kiểm tra trước khi
     * gọi).
     *
     * Hai lần đăng nhập cùng lúc có thể làm insert identity ném
     * DuplicateKeyException. Postgres huỷ cả transaction
     * khi một câu lệnh lỗi, nên không thử lại trong hàm này được: nơi gọi bắt lỗi
     * rồi gọi lại (transaction mới).
     */
    @Transactional
    public User findOrCreateUser(String provider, String providerUserId, String email, String displayName) {
        Objects.requireNonNull(email, "OAuth login requires a verified email");

        // 1. Đã từng đăng nhập bằng tài khoản Google/GitHub này
        Optional<UUID> linkedUserId = identityRepository.findUserId(provider, providerUserId);
        if (linkedUserId.isPresent()) {
            return userRepository.findById(linkedUserId.get())
                    // user_identities có ON DELETE CASCADE nên không thể xảy ra, trừ khi dữ liệu
                    // hỏng
                    .orElseThrow(
                            () -> new IllegalStateException("Identity points to missing user " + linkedUserId.get()));
        }

        // 2. Đã có tài khoản cùng email (đăng ký bằng mật khẩu, hoặc provider còn lại)
        // → liên kết vào
        String normalizedEmail = EmailUtils.normalize(email);
        Optional<UserCredentials> existing = userRepository.findCredentialsByEmail(normalizedEmail);
        if (existing.isPresent()) {
            User user = existing.get().user();
            // Provider đã xác nhận chủ email → đánh dấu đã xác minh. Tài khoản cũ chưa từng xác minh thì bị xoá
            // mật khẩu (có thể kẻ gian đăng ký trước bằng email này); đã xác minh thì vẫn đăng nhập bằng mật khẩu được.
            userRepository.markEmailVerifiedByProvider(user.id());
            identityRepository.insert(user.id(), provider, providerUserId);
            return user;
        }

        // 3. Người mới: tạo user không có mật khẩu
        User created = userRepository.insertUser(normalizedEmail, null, toDisplayName(displayName, normalizedEmail));
        identityRepository.insert(created.id(), provider, providerUserId);
        return created;
    }

    /**
     * Mã đăng nhập dùng 1 lần, gồm 2 nửa:
     * - code: đi trên URL (/oauth/callback?code=...), frontend gửi lại trong body của /auth/oauth/exchange
     * - nonce: chỉ nằm trong cookie HttpOnly đặt cho đúng trình duyệt vừa đăng nhập Google/GitHub
     * Redis chỉ có key từ CẢ HAI nửa. Ai đó dụ người khác mở link /oauth/callback?code=<mã của hắn> (login CSRF:
     * ép nạn nhân đăng nhập vào tài khoản kẻ gian rồi đọc trộm những gì nạn nhân chat) thì trình duyệt nạn nhân
     * không có cookie → không đổi được mã.
     */
    public record LoginCode(String code, String nonce) {
    }

    public LoginCode createLoginCode(UUID userId) {
        LoginCode loginCode = new LoginCode(RandomCode.generate(LOGIN_CODE_LENGTH), RandomCode.generate(LOGIN_CODE_LENGTH));
        redis.opsForValue().set(loginCodeKey(loginCode.code(), loginCode.nonce()), userId.toString(), LOGIN_CODE_TTL);
        return loginCode;
    }

    /** Đổi mã lấy userId; rỗng nếu sai, hết hạn, đã dùng, hoặc cookie không khớp. getAndDelete: chỉ dùng được 1 lần */
    public Optional<UUID> consumeLoginCode(String code, String nonce) {
        if (code == null || nonce == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(redis.opsForValue().getAndDelete(loginCodeKey(code, nonce))).map(UUID::fromString);
    }

    private static String loginCodeKey(String code, String nonce) {
        return LOGIN_CODE_KEY_PREFIX + HashUtils.sha256(code + ":" + nonce);
    }

    /** Tên trống thì lấy phần trước @ của email; dài quá 256 ký tự thì cắt bớt */
    private static String toDisplayName(String displayName, String email) {
        String name = displayName == null || displayName.isBlank() ? email.substring(0, email.indexOf('@'))
                : displayName.strip();
        int length = name.codePointCount(0, name.length());
        if (length <= MAX_DISPLAY_NAME_LENGTH) {
            return name;
        }
        // Cắt theo code point (giống cách Postgres đếm ký tự), không cắt đôi emoji
        return name.substring(0, name.offsetByCodePoints(0, MAX_DISPLAY_NAME_LENGTH)).strip();
    }
}
