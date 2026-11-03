package com.llmchad.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.llmchad.auth.dto.LoginRequest;
import com.llmchad.auth.dto.LoginResponse;
import com.llmchad.auth.dto.RefreshRequest;
import com.llmchad.auth.dto.RegisterRequest;
import com.llmchad.auth.dto.TokenResponse;
import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;
import com.llmchad.auth.repository.UserRepository;
import com.llmchad.common.MessageKey;
import com.llmchad.exception.AppException;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository, UserService userService, PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public void register(RegisterRequest payload) {
        if (userRepository.existEmail(payload.email())) {
            throw new AppException(HttpStatus.CONFLICT, MessageKey.EMAIL_ALREADY_EXISTS);
        }
        userRepository.insertUser(payload.email(), passwordEncoder.encode(payload.password()), payload.displayName());
    }

    public TokenResponse refresh(RefreshRequest payload) {
        User user = userService.getUserById(tokenService.consumeRefreshToken(payload.refreshToken()));
        String accessToken = tokenService.createAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user.id());
        return new TokenResponse(accessToken, "Bearer", tokenService.getAccessTtlSeconds(), refreshToken);
    }

    public LoginResponse login(LoginRequest payload) {
        UserCredentials userCredentials = userRepository.findCredentialsByEmail(payload.email())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, MessageKey.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(payload.password(), userCredentials.passwordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, MessageKey.INVALID_CREDENTIALS);
        }

        String accessToken = tokenService.createAccessToken(userCredentials.user());
        String refreshToken = tokenService.createRefreshToken(userCredentials.user().id());

        return new LoginResponse(accessToken, "Bearer", tokenService.getAccessTtlSeconds(), refreshToken,
                userCredentials.user());
    }

    public void logout(String token) {
        tokenService.revokeRefreshToken(token);
    }
}
