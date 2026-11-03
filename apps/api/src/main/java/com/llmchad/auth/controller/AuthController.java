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
import com.llmchad.auth.dto.TokenResponse;
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
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, MessageKey.REGISTER_SUCCESS, null));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest refreshRequest) {
        return ApiResponse.success(HttpStatus.OK, null, authService.refresh(refreshRequest));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest logoutRequest) {
        authService.logout(logoutRequest.refreshToken());
        return ResponseEntity.noContent().build();
    }

}
