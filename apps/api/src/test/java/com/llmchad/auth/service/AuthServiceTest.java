package com.llmchad.auth.service;

import static com.llmchad.support.TestAssertions.assertAppException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.llmchad.auth.dto.LoginRequest;
import com.llmchad.auth.dto.LoginResponse;
import com.llmchad.auth.dto.RefreshRequest;
import com.llmchad.auth.dto.RegisterRequest;
import com.llmchad.auth.dto.TokenResponse;
import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;
import com.llmchad.auth.repository.UserRepository;
import com.llmchad.common.MessageKey;

/** Unit test thuần Mockito: không khởi động Spring, mọi dependency đều là mock. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthService authService;

    private final User user = new User(UUID.randomUUID(), "an@example.com", "An", Instant.now());

    // ---------- register ----------

    @Test
    void register_emailAlreadyExists_throwsConflict() {
        when(userRepository.existEmail("an@example.com")).thenReturn(true);

        assertAppException(() -> authService.register(new RegisterRequest("an@example.com", "password123", "An")),
                HttpStatus.CONFLICT, MessageKey.EMAIL_ALREADY_EXISTS);
        verify(userRepository, never()).insertUser(anyString(), anyString(), anyString());
    }

    @Test
    void register_newEmail_savesHashedPassword() {
        when(userRepository.existEmail("an@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        authService.register(new RegisterRequest("an@example.com", "password123", "An"));

        verify(userRepository).insertUser("an@example.com", "hashed", "An");
    }

    // ---------- login ----------

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findCredentialsByEmail("an@example.com")).thenReturn(Optional.empty());

        assertAppException(() -> authService.login(new LoginRequest("an@example.com", "password123")),
                HttpStatus.UNAUTHORIZED, MessageKey.INVALID_CREDENTIALS);
        verify(tokenService, never()).createAccessToken(any());
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        when(userRepository.findCredentialsByEmail("an@example.com"))
                .thenReturn(Optional.of(new UserCredentials(user, "hashed")));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertAppException(() -> authService.login(new LoginRequest("an@example.com", "wrong-password")),
                HttpStatus.UNAUTHORIZED, MessageKey.INVALID_CREDENTIALS);
        verify(tokenService, never()).createAccessToken(any());
        verify(tokenService, never()).createRefreshToken(any());
    }

    @Test
    void login_correctPassword_returnsTokensAndUser() {
        when(userRepository.findCredentialsByEmail("an@example.com"))
                .thenReturn(Optional.of(new UserCredentials(user, "hashed")));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(tokenService.createAccessToken(user)).thenReturn("access");
        when(tokenService.createRefreshToken(user.id())).thenReturn("refresh");
        when(tokenService.getAccessTtlSeconds()).thenReturn(900L);

        LoginResponse res = authService.login(new LoginRequest("an@example.com", "password123"));

        assertThat(res.accessToken()).isEqualTo("access");
        assertThat(res.refreshToken()).isEqualTo("refresh");
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.expiresIn()).isEqualTo(900L);
        assertThat(res.user()).isEqualTo(user);
    }

    // ---------- refresh / logout ----------

    @Test
    void refresh_consumesOldTokenAndIssuesNewPair() {
        when(tokenService.consumeRefreshToken("old-refresh")).thenReturn(user.id());
        when(userService.getUserById(user.id())).thenReturn(user);
        when(tokenService.createAccessToken(user)).thenReturn("new-access");
        when(tokenService.createRefreshToken(user.id())).thenReturn("new-refresh");
        when(tokenService.getAccessTtlSeconds()).thenReturn(900L);

        TokenResponse res = authService.refresh(new RefreshRequest("old-refresh"));

        assertThat(res).isEqualTo(new TokenResponse("new-access", "Bearer", 900L, "new-refresh"));
    }

    @Test
    void logout_revokesRefreshToken() {
        authService.logout("refresh");

        verify(tokenService).revokeRefreshToken("refresh");
    }
}
