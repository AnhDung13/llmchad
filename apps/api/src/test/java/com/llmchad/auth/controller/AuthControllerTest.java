package com.llmchad.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.llmchad.auth.dto.RegisterRequest;
import com.llmchad.auth.model.User;
import com.llmchad.auth.service.AuthService;
import com.llmchad.common.MessageKey;
import com.llmchad.config.SecurityConfig;
import com.llmchad.exception.AppException;

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
                                """.formatted("a".repeat(51)))
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
}
