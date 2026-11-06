package com.llmchad.auth.dto;

/**
 * Trả về sau khi gửi mã xác minh (register, resend-verification).
 * Frontend dùng để đếm ngược, không phải tự đoán thời hạn.
 *
 * @param expiresIn   số giây mã còn hiệu lực
 * @param resendAfter số giây phải chờ trước khi được gửi lại mã
 */
public record VerificationCodeResponse(long expiresIn, long resendAfter) {
}
