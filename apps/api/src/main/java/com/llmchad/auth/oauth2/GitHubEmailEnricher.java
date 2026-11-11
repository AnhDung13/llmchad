package com.llmchad.auth.oauth2;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GitHubEmailEnricher implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private static final ParameterizedTypeReference<List<Map<String, Object>>> EMAIL_LIST = new ParameterizedTypeReference<>() {
    };

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient github = RestClient.builder()
            .baseUrl("https://api.github.com")
            // GitHub treo thì mỗi lần đăng nhập giữ một thread của Tomcat mãi mãi → chặn cả API và chat
            .requestFactory(timeouts())
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .build();

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        // Thực tế chỉ GitHub đi qua đây (Google có scope openid nên Spring dùng OidcUserService riêng).
        // Kiểm tra cho chắc nếu sau này thêm provider không dùng OIDC.
        if (!"github".equals(userRequest.getClientRegistration().getRegistrationId())) {
            return user;
        }

        Map<String, Object> merged = new HashMap<>(user.getAttributes());
        merged.put("email", findPrimaryVerifiedEmail(userRequest.getAccessToken().getTokenValue()));
        return new DefaultOAuth2User(user.getAuthorities(), merged, "id");
    }

    private String findPrimaryVerifiedEmail(String accessToken) {
        List<Map<String, Object>> emails;
        try {
            emails = github.get().uri("/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(EMAIL_LIST);
        } catch (RestClientException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("github_emails_failed"), e);
        }
        if (emails == null) {
            return null;
        }
        for (Map<String, Object> emailEntry : emails) {
            if (Boolean.TRUE.equals(emailEntry.get("primary")) && Boolean.TRUE.equals(emailEntry.get("verified"))) {
                return (String) emailEntry.get("email");
            }
        }
        return null;
    }
}
