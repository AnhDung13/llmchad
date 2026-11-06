package com.llmchad.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

// Băm SHA-256 cho token/mã ngẫu nhiên trước khi lưu (refresh token, mã xác minh email, mã OAuth...).
// Không dùng cho mật khẩu: mật khẩu phải dùng PasswordEncoder (BCrypt) vì SHA-256 băm quá nhanh, dễ bị dò.
public final class HashUtils {
    private HashUtils() {
    }

    // Kết quả là Base64 URL-safe không padding (43 ký tự), dùng thẳng làm một phần của key Redis được
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            // Mọi JVM đều bắt buộc có SHA-256, nên không bao giờ xảy ra
            throw new IllegalStateException(e);
        }
    }
}
