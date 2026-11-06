package com.llmchad.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.llmchad.auth.dto.LoginRequest;
import com.llmchad.auth.dto.LoginResponse;
import com.llmchad.auth.dto.LogoutRequest;
import com.llmchad.auth.dto.RefreshRequest;
import com.llmchad.auth.dto.RegisterRequest;
import com.llmchad.auth.dto.ResendVerificationRequest;
import com.llmchad.auth.dto.TokenResponse;
import com.llmchad.auth.dto.VerificationCodeResponse;
import com.llmchad.auth.dto.VerifyEmailRequest;
import com.llmchad.auth.service.AuthService;
import com.llmchad.common.ApiResponse;
import com.llmchad.common.MessageKey;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse loginResponse = authService.login(loginRequest);
        return ApiResponse.success(HttpStatus.OK, MessageKey.LOGIN_SUCCESS, loginResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<VerificationCodeResponse>> register(
            @Valid @RequestBody RegisterRequest registerRequest) {
        // Chưa tạo user: chỉ gửi mã xác minh, tạo user ở /verify-email
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, MessageKey.REGISTER_SUCCESS,
                        authService.register(registerRequest)));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest refreshRequest) {
        return ApiResponse.success(HttpStatus.OK, null, authService.refresh(refreshRequest));
    }

    @PostMapping("/verify-email")
    public ApiResponse<LoginResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest verifyEmailRequest) {
        // Xác minh xong là tạo tài khoản và đăng nhập luôn: response giống hệt /login
        return ApiResponse.success(HttpStatus.OK, MessageKey.EMAIL_VERIFIED, authService.verifyEmail(verifyEmailRequest));
    }

    @PostMapping("/resend-verification")
    public ApiResponse<VerificationCodeResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest resendVerificationRequest) {
        return ApiResponse.success(HttpStatus.OK, MessageKey.VERIFICATION_SENT,
                authService.resendVerificationCode(resendVerificationRequest));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest logoutRequest) {
        authService.logout(logoutRequest.refreshToken());
        return ResponseEntity.noContent().build();
    }

}
