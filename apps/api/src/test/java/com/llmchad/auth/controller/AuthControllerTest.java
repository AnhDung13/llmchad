package com.llmchad.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.llmchad.auth.dto.LoginRequest;
import com.llmchad.auth.dto.LoginResponse;
import com.llmchad.auth.dto.ResetLinkSentResponse;
import com.llmchad.auth.dto.RegisterRequest;
import com.llmchad.auth.model.User;
import com.llmchad.auth.service.AuthService;
import com.llmchad.auth.service.PasswordResetService;
import com.llmchad.common.MessageKey;
import com.llmchad.config.SecurityConfig;
import com.llmchad.exception.AppException;

import jakarta.servlet.http.Cookie;

/**
 * @WebMvcTest chỉ dựng tầng web: controller, @RestControllerAdvice, WebConfig
 *             (prefix /api).
 *             SecurityConfig phải @Import tay vì nó là @Configuration thường.
 *             Service là mock.
 */
@WebMvcTest(controllers = AuthController.class, properties = "app.jwt.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQh")
@Import(SecurityConfig.class)
class AuthControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AuthService authService;

        @MockitoBean
        private PasswordResetService passwordResetService;

        private ResultActions postJson(String url, String body) throws Exception {
                return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
        }

        // ---------- login ----------

        @Test
        void login_valid_returns200WithTokens() throws Exception {
                User user = new User(UUID.randomUUID(), "an@example.com", "An", Instant.parse("2026-01-01T00:00:00Z"));
                when(authService.login(new LoginRequest("an@example.com", "password123")))
                                .thenReturn(new LoginResponse("access", "Bearer", 900, "refresh", user));

                postJson("/api/auth/login", """
                                {"email": "an@example.com", "password": "password123"}
                                """)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value(200))
                                .andExpect(jsonPath("$.message").value(MessageKey.LOGIN_SUCCESS))
                                .andExpect(jsonPath("$.data.accessToken").value("access"))
                                .andExpect(jsonPath("$.data.refreshToken").value("refresh"))
                                .andExpect(jsonPath("$.data.user.email").value("an@example.com"));
        }

        @Test
        void login_invalidEmail_returns400WithKey() throws Exception {
                postJson("/api/auth/login", """
                                {"email": "not-an-email", "password": "password123"}
                                """)
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.EMAIL_INVALID));
                verifyNoInteractions(authService);
        }

        @Test
        void login_missingPassword_returns400Required() throws Exception {
                postJson("/api/auth/login", """
                                {"email": "an@example.com"}
                                """)
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.FIELD_REQUIRED));
        }

        @Test
        void login_wrongCredentials_returns401() throws Exception {
                when(authService.login(any()))
                                .thenThrow(new AppException(HttpStatus.UNAUTHORIZED, MessageKey.INVALID_CREDENTIALS));

                postJson("/api/auth/login", """
                                {"email": "an@example.com", "password": "wrong-password"}
                                """)
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.status").value(401))
                                .andExpect(jsonPath("$.message").value(MessageKey.INVALID_CREDENTIALS))
                                .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        void login_malformedJson_returns400() throws Exception {
                postJson("/api/auth/login", "{not json")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.MALFORMED_JSON));
        }

        // ---------- register ----------

        @Test
        void register_valid_returns201() throws Exception {
                postJson("/api/auth/register", """
                                {"email": "an@example.com", "password": "password123", "displayName": "An"}
                                """)
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value(201))
                                .andExpect(jsonPath("$.message").value(MessageKey.REGISTER_SUCCESS));
                verify(authService).register(new RegisterRequest("an@example.com", "password123", "An"));
        }

        @Test
        void register_shortPassword_returns400() throws Exception {
                postJson("/api/auth/register", """
                                {"email": "an@example.com", "password": "short", "displayName": "An"}
                                """)
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.PASSWORD_TOO_SHORT));
        }

        @Test
        void register_displayNameTooLong_returns400() throws Exception {
                postJson("/api/auth/register", """
                                {"email": "an@example.com", "password": "password123", "displayName": "%s"}
                                """.formatted("a".repeat(257)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.DISPLAY_NAME_TOO_LONG));
        }

        @Test
        void register_emailTaken_returns409() throws Exception {
                doThrow(new AppException(HttpStatus.CONFLICT, MessageKey.EMAIL_ALREADY_EXISTS))
                                .when(authService).register(any());

                postJson("/api/auth/register", """
                                {"email": "an@example.com", "password": "password123", "displayName": "An"}
                                """)
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value(MessageKey.EMAIL_ALREADY_EXISTS));
        }

        // ---------- refresh / logout ----------

        @Test
        void refresh_blankToken_returns400() throws Exception {
                postJson("/api/auth/refresh", """
                                {"refreshToken": ""}
                                """)
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.FIELD_REQUIRED));
        }

        @Test
        void logout_returns204AndRevokes() throws Exception {
                postJson("/api/auth/logout", """
                                {"refreshToken": "refresh"}
                                """)
                                .andExpect(status().isNoContent());
                verify(authService).logout("refresh");
        }

        // ---------- oauth/exchange ----------

        @Test
        void oauthExchange_validCodeWithCookie_returns200AndClearsCookie() throws Exception {
                User user = new User(UUID.randomUUID(), "an@example.com", "An", Instant.now());
                when(authService.loginWithOAuthCode("one-time-code", "nonce"))
                                .thenReturn(new LoginResponse("access", "Bearer", 900, "refresh", user));

                // Không có header Authorization: endpoint nằm trong permitAll
                mockMvc.perform(post("/api/auth/oauth/exchange").contentType(MediaType.APPLICATION_JSON)
                                .cookie(new Cookie("oauth_login_nonce", "nonce"))
                                .content("""
                                                {"code": "one-time-code"}
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value(MessageKey.LOGIN_SUCCESS))
                                .andExpect(jsonPath("$.data.accessToken").value("access"))
                                .andExpect(header().string("Set-Cookie",
                                                org.hamcrest.Matchers.containsString("oauth_login_nonce=;")))
                                .andExpect(header().string("Set-Cookie",
                                                org.hamcrest.Matchers.containsString("Max-Age=0")));
        }

        @Test
        void oauthExchange_withoutCookie_passesNullNonce_returns401() throws Exception {
                // Trình duyệt khác (login CSRF) không có cookie → service nhận nonce null → mã không hợp lệ
                when(authService.loginWithOAuthCode("one-time-code", null))
                                .thenThrow(new AppException(HttpStatus.UNAUTHORIZED, MessageKey.INVALID_OAUTH_CODE));

                postJson("/api/auth/oauth/exchange", """
                                {"code": "one-time-code"}
                                """)
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.message").value(MessageKey.INVALID_OAUTH_CODE));
        }

        @Test
        void oauthExchange_blankCode_returns400() throws Exception {
                postJson("/api/auth/oauth/exchange", """
                                {"code": ""}
                                """)
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.FIELD_REQUIRED));
                verifyNoInteractions(authService);
        }

        // ---------- quên mật khẩu ----------

        @Test
        void forgotPassword_returns200WithTimings_withoutLogin() throws Exception {
                when(passwordResetService.requestReset("an@example.com"))
                                .thenReturn(new ResetLinkSentResponse(900, 60));

                postJson("/api/auth/forgot-password", """
                                {"email": "an@example.com"}
                                """)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value(MessageKey.PASSWORD_RESET_SENT))
                                .andExpect(jsonPath("$.data.expiresIn").value(900))
                                .andExpect(jsonPath("$.data.resendAfter").value(60));
        }

        @Test
        void forgotPassword_invalidEmail_returns400() throws Exception {
                postJson("/api/auth/forgot-password", """
                                {"email": "not-an-email"}
                                """)
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.EMAIL_INVALID));
                verifyNoInteractions(passwordResetService);
        }

        @Test
        void validateResetToken_expired_returns410() throws Exception {
                doThrow(new AppException(HttpStatus.GONE, MessageKey.RESET_TOKEN_INVALID))
                                .when(passwordResetService).validateToken("old-token");

                postJson("/api/auth/reset-password/validate", """
                                {"token": "old-token"}
                                """)
                                .andExpect(status().isGone())
                                .andExpect(jsonPath("$.message").value(MessageKey.RESET_TOKEN_INVALID));
        }

        @Test
        void resetPassword_valid_returns200() throws Exception {
                postJson("/api/auth/reset-password", """
                                {"token": "reset-token", "newPassword": "newpassword123"}
                                """)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value(MessageKey.PASSWORD_RESET_SUCCESS));
                verify(passwordResetService).resetPassword("reset-token", "newpassword123");
        }

        @Test
        void resetPassword_shortPassword_returns400WithoutUsingToken() throws Exception {
                postJson("/api/auth/reset-password", """
                                {"token": "reset-token", "newPassword": "short"}
                                """)
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.PASSWORD_TOO_SHORT));
                verifyNoInteractions(passwordResetService);
        }
}
