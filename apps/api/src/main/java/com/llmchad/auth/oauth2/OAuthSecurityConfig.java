package com.llmchad.auth.oauth2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Chain riêng cho đăng nhập Google/GitHub, chạy trước chain API (SecurityConfig, @Order(2)).
 * Tách khỏi SecurityConfig để các test @WebMvcTest (chỉ @Import SecurityConfig) không phải dựng cả phần OAuth.
 *
 * Đường dẫn mặc định của Spring không có /api (Caddy chỉ chuyển /api/* sang backend) nên phải đổi cả hai:
 *   GET /api/oauth2/authorization/{google|github}  → redirect sang trang đồng ý của provider
 *   GET /api/login/oauth2/code/{google|github}      → provider gọi về, Spring đổi code lấy thông tin user
 * Chain này cần session (Spring lưu `state` chống CSRF giữa lúc đi và về), chain API thì STATELESS.
 */
@Configuration
public class OAuthSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain oauthChain(HttpSecurity http, GitHubEmailEnricher githubEmailEnricher,
            OAuthLoginSuccessHandler successHandler, OAuthLoginFailureHandler failureHandler) throws Exception {
        http.securityMatcher("/api/oauth2/**", "/api/login/oauth2/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .oauth2Login(o -> o
                        .authorizationEndpoint(a -> a.baseUri("/api/oauth2/authorization"))
                        .redirectionEndpoint(r -> r.baseUri("/api/login/oauth2/code/*"))
                        .userInfoEndpoint(u -> u.userService(githubEmailEnricher))
                        // Mặc định Spring giữ access token Google/GitHub của mọi người trong một map trên RAM, không
                        // bao giờ xoá (app không dùng lại token đó). Để trong session: success handler huỷ session
                        // ngay sau khi đăng nhập, token mất theo.
                        .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }
}
