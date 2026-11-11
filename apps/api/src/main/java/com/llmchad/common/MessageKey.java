package com.llmchad.common;

// Message key trả về trong field `message` của ApiResponse (thay cho câu chữ viết cứng).
// Frontend dịch key → câu theo ngôn ngữ người dùng: apps/web/src/locales/{vi,en}/server.json
// Thêm key mới ở đây thì nhớ thêm vào cả hai file server.json.
//
// Dùng hằng String thay vì enum để đặt được vào annotation validation,
// ví dụ @Size(min = 8, message = MessageKey.PASSWORD_TOO_SHORT) — thuộc tính annotation phải là hằng số lúc compile.
public final class MessageKey {
    private MessageKey() {
    }

    // Xác thực
    public static final String LOGIN_SUCCESS = "auth.loginSuccess";
    public static final String REGISTER_SUCCESS = "auth.registerSuccess";
    public static final String LOGOUT_SUCCESS = "auth.logoutSuccess";
    public static final String EMAIL_ALREADY_EXISTS = "auth.emailAlreadyExists";
    public static final String INVALID_CREDENTIALS = "auth.invalidCredentials";
    public static final String INVALID_REFRESH_TOKEN = "auth.invalidRefreshToken";
    public static final String UNAUTHORIZED = "auth.unauthorized";

    // Xác minh email (mã 6 ký tự gửi qua mail, xem docs/api-contract.md → Bước 8)
    public static final String EMAIL_VERIFIED = "auth.emailVerified";
    public static final String VERIFICATION_SENT = "auth.verificationSent";
    public static final String INVALID_VERIFY_CODE = "auth.invalidVerifyCode";
    public static final String VERIFICATION_EXPIRED = "auth.verificationExpired";
    public static final String VERIFY_CODE_EXPIRED = "auth.verifyCodeExpired";
    public static final String VERIFY_ATTEMPTS_EXCEEDED = "auth.verifyAttemptsExceeded";
    public static final String TOO_MANY_REQUESTS = "auth.tooManyRequests";
    public static final String SEND_MAIL_FAILED = "auth.sendMailFailed";

    // Quên mật khẩu (link trong mail, xem docs/api-contract.md → Bước 10)
    public static final String PASSWORD_RESET_SENT = "auth.passwordResetSent";
    public static final String PASSWORD_RESET_SUCCESS = "auth.passwordResetSuccess";
    public static final String RESET_TOKEN_INVALID = "auth.resetTokenInvalid";

    // OAuth (Google, GitHub)
    public static final String INVALID_OAUTH_CODE = "auth.invalidOAuthCode";
    public static final String OAUTH_FAILED = "auth.oauthFailed";
    public static final String OAUTH_EMAIL_UNVERIFIED = "auth.oauthEmailUnverified";

    // Validation
    public static final String VALIDATION_FAILED = "validation.failed";
    public static final String FIELD_REQUIRED = "validation.required";
    public static final String EMAIL_INVALID = "validation.emailInvalid";
    public static final String PASSWORD_TOO_SHORT = "validation.passwordTooShort";
    public static final String DISPLAY_NAME_TOO_LONG = "validation.displayNameTooLong";
    public static final String MALFORMED_JSON = "validation.malformedJson";
    public static final String TYPE_MISSMATCH = "validation.argumentTypeMismatch";
    public static final String TITLE_TOO_LONG = "validation.titleTooLong";
    public static final String PAGE_INVALID = "validation.pageInvalid";
    public static final String PAGE_SIZE_INVALID = "validation.pageSizeInvalid";

    // Hội thoại, LLM (bước 4–6)
    public static final String CONVERSATION_NOT_FOUND = "conversation.notFound";
    public static final String LLM_FAILED = "llm.failed";

    // Lỗi chung
    public static final String NOT_FOUND = "error.notFound";
    public static final String METHOD_NOT_ALLOWED = "error.methodNotAllowed";
    public static final String INTERNAL_ERROR = "error.internal";
}
