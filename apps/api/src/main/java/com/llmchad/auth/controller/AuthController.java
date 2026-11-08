package com.llmchad.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.llmchad.auth.dto.LoginRequest;
import com.llmchad.auth.dto.LoginResponse;
import com.llmchad.auth.dto.LogoutRequest;
import com.llmchad.auth.dto.OAuthExchangeRequest;
import com.llmchad.auth.dto.RefreshRequest;
import com.llmchad.auth.dto.RegisterRequest;
import com.llmchad.auth.dto.ResendVerificationRequest;
import com.llmchad.auth.dto.TokenResponse;
import com.llmchad.auth.dto.VerificationCodeResponse;
import com.llmchad.auth.dto.VerifyEmailRequest;
import com.llmchad.auth.oauth2.OAuthLoginService;
import com.llmchad.auth.service.AuthService;
import com.llmchad.common.ApiResponse;
import com.llmchad.common.MessageKey;

import jakarta.servlet.http.HttpServletResponse;
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
        return ApiResponse.success(HttpStatus.OK, MessageKey.EMAIL_VERIFIED,
                authService.verifyEmail(verifyEmailRequest));
    }

    @PostMapping("/resend-verification")
    public ApiResponse<VerificationCodeResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest resendVerificationRequest) {
        return ApiResponse.success(HttpStatus.OK, MessageKey.VERIFICATION_SENT,
                authService.resendVerificationCode(resendVerificationRequest));
    }

    @PostMapping("/oauth/exchange")
    public ApiResponse<LoginResponse> exchangeOAuthCode(@Valid @RequestBody OAuthExchangeRequest oauthExchangeRequest,
            @CookieValue(name = OAuthLoginService.LOGIN_NONCE_COOKIE, required = false) String nonce,
            HttpServletResponse response) {
        // Mã chỉ dùng 1 lần: xoá cookie dù đổi thành công hay không
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(OAuthLoginService.LOGIN_NONCE_COOKIE, "")
                .path(OAuthLoginService.LOGIN_NONCE_COOKIE_PATH).maxAge(0).build().toString());
        return ApiResponse.success(HttpStatus.OK, MessageKey.LOGIN_SUCCESS,
                authService.loginWithOAuthCode(oauthExchangeRequest.code(), nonce));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest logoutRequest) {
        authService.logout(logoutRequest.refreshToken());
        return ResponseEntity.noContent().build();
    }

}
