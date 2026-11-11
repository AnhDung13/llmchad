package com.llmchad.common;

import java.util.Locale;

public final class EmailUtils {
    private EmailUtils() {
    }

    // Gmail, Outlook... không phân biệt hoa thường: An@x.com và an@x.com là cùng một hộp thư.
    // Mọi chỗ nhận email (đăng ký, đăng nhập, OAuth) phải gọi hàm này, nếu không cùng một người sẽ thành hai tài khoản.
    // Locale.ROOT: máy chạy tiếng Thổ Nhĩ Kỳ sẽ biến "I" thành "ı" nếu dùng locale mặc định.
    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
