package com.llmchad.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.llmchad.auth.model.User;
import com.llmchad.common.MessageKey;

/** Không dựng Spring: request/response giả của spring-test, OAuthLoginService là mock. */
class OAuthLoginSuccessHandlerTest {

    private OAuthLoginService service;
    private OAuthLoginSuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final User user = new User(UUID.randomUUID(), "an@example.com", "An", Instant.now());

    @BeforeEach
    void setUp() {
        service = mock(OAuthLoginService.class);
        handler = new OAuthLoginSuccessHandler(service);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private OAuth2AuthenticationToken token(String provider, Map<String, Object> attributes, String nameKey) {
        DefaultOAuth2User principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                attributes, nameKey);
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), provider);
    }

    private OAuth2AuthenticationToken google(boolean emailVerified) {
        return token("google", Map.of("sub", "sub-1", "email", "An@Example.com", "email_verified", emailVerified,
                "name", "An Nguyen"), "sub");
    }

    /** GitHub: email do GitHubEmailEnricher điền (null = không có email chính đã xác minh) */
    private OAuth2AuthenticationToken github(String email, String name) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 42);
        attributes.put("login", "an-dev");
        attributes.put("email", email);
        attributes.put("name", name);
        return token("github", attributes, "id");
    }

    @Test
    void google_verifiedEmail_redirectsToCallbackWithCode() throws Exception {
        when(service.findOrCreateUser("google", "sub-1", "An@Example.com", "An Nguyen")).thenReturn(user);
        when(service.createLoginCode(user.id())).thenReturn(new OAuthLoginService.LoginCode("one-time-code", "nonce-1"));

        handler.onAuthenticationSuccess(request, response, google(true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth/callback?code=one-time-code");
        // Nửa còn lại chỉ nằm trong cookie HttpOnly của trình duyệt này
        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("oauth_login_nonce=nonce-1")
                .contains("Path=/api/auth/oauth", "Max-Age=60", "HttpOnly", "SameSite=Lax")
                .doesNotContain("one-time-code");
    }

    @Test
    void google_unverifiedEmail_redirectsToLoginWithoutCreatingUser() throws Exception {
        handler.onAuthenticationSuccess(request, response, google(false));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=" + MessageKey.OAUTH_EMAIL_UNVERIFIED);
        verify(service, never()).findOrCreateUser(anyString(), anyString(), any(), any());
    }

    @Test
    void github_numericIdBecomesString_andMissingNameFallsBackToLogin() throws Exception {
        when(service.findOrCreateUser("github", "42", "an@example.com", "an-dev")).thenReturn(user);
        when(service.createLoginCode(user.id())).thenReturn(new OAuthLoginService.LoginCode("code", "nonce-1"));

        handler.onAuthenticationSuccess(request, response, github("an@example.com", null));

        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth/callback?code=code");
    }

    @Test
    void github_noVerifiedEmail_redirectsToLoginWithoutCreatingUser() throws Exception {
        handler.onAuthenticationSuccess(request, response, github(null, "An"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=" + MessageKey.OAUTH_EMAIL_UNVERIFIED);
        verify(service, never()).findOrCreateUser(anyString(), anyString(), any(), any());
    }

    @Test
    void concurrentLogin_duplicateKey_retriesOnce() throws Exception {
        when(service.findOrCreateUser("google", "sub-1", "An@Example.com", "An Nguyen"))
                .thenThrow(new DuplicateKeyException("user_identities_provider_provider_user_id_key"))
                .thenReturn(user);
        when(service.createLoginCode(user.id())).thenReturn(new OAuthLoginService.LoginCode("code", "nonce-1"));

        handler.onAuthenticationSuccess(request, response, google(true));

        verify(service, times(2)).findOrCreateUser("google", "sub-1", "An@Example.com", "An Nguyen");
        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth/callback?code=code");
    }

    @Test
    void databaseError_redirectsToLoginInsteadOf500() throws Exception {
        when(service.findOrCreateUser(anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("DB down"));

        handler.onAuthenticationSuccess(request, response, google(true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=" + MessageKey.OAUTH_FAILED);
    }

    @Test
    void github_missingId_redirectsToLoginInsteadOfLinkingToUserNamedNull() throws Exception {
        Map<String, Object> attributes = new HashMap<>(Map.of("login", "an-dev", "email", "an@example.com"));
        attributes.put("id", null);

        handler.onAuthenticationSuccess(request, response, token("github", attributes, "login"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=" + MessageKey.OAUTH_FAILED);
        verify(service, never()).findOrCreateUser(anyString(), anyString(), any(), any());
    }

    @Test
    void unknownProvider_redirectsToLogin() throws Exception {
        handler.onAuthenticationSuccess(request, response, token("facebook", Map.of("id", "1"), "id"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=" + MessageKey.OAUTH_FAILED);
    }

    @Test
    void invalidatesTheTemporaryOAuthSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        handler.onAuthenticationSuccess(request, response, google(false));

        assertThat(session.isInvalid()).isTrue();
    }
}
