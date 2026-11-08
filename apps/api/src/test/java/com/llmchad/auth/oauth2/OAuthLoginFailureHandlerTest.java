package com.llmchad.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import com.llmchad.common.MessageKey;

class OAuthLoginFailureHandlerTest {

    @Test
    void userCancelled_redirectsToLoginAndDropsTemporarySession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuthLoginFailureHandler().onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=" + MessageKey.OAUTH_FAILED);
        // Không để lại session trên RAM sau mỗi lần bấm Huỷ
        assertThat(session.isInvalid()).isTrue();
    }
}
