package com.llmchad.auth.oauth2;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.llmchad.auth.model.User;
import com.llmchad.common.MessageKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Google/GitHub đã xác nhận người dùng → tìm/tạo user của mình, rồi đưa về
 * frontend kèm mã dùng 1 lần.
 * Frontend (/oauth/callback) đổi mã đó lấy access/refresh token qua POST
 * /api/auth/oauth/exchange.
 * Không đặt token lên URL: URL nằm lại trong lịch sử trình duyệt và log của
 * proxy.
 */
@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuthLoginSuccessHandler.class);

    // Đường dẫn tương đối: trình duyệt đang ở localhost:5173 (dev, qua Vite) hoặc
    // llmchad.site (prod)
    static final String CALLBACK_PATH = "/oauth/callback?code=";
    static final String LOGIN_ERROR_PATH = "/login?oauthError=";

    private final OAuthLoginService oauthLoginService;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public OAuthLoginSuccessHandler(OAuthLoginService oauthLoginService) {
        this.oauthLoginService = oauthLoginService;
    }

    /** Thông tin cần từ provider, đã quy về cùng một dạng cho Google và GitHub */
    record ProviderProfile(String providerUserId, String email, boolean emailVerified, String displayName) {
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        // Session chỉ cần để giữ `state` giữa lúc đi và về; xong rồi thì bỏ, API của
        // mình dùng JWT
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId();
        ProviderProfile profile = toProfile(provider, token.getPrincipal());
        if (profile == null) {
            redirectToLogin(request, response, MessageKey.OAUTH_FAILED);
            return;
        }
        // Chỉ tin email provider đã xác minh, nếu không ai cũng liên kết được vào tài
        // khoản người khác
        if (profile.email() == null || !profile.emailVerified()) {
            redirectToLogin(request, response, MessageKey.OAUTH_EMAIL_UNVERIFIED);
            return;
        }

        OAuthLoginService.LoginCode loginCode;
        try {
            User user = findOrCreateUser(provider, profile);
            loginCode = oauthLoginService.createLoginCode(user.id());
        } catch (RuntimeException e) {
            // DB/Redis lỗi: đưa về trang đăng nhập có thông báo thay vì trang lỗi 500 trắng
            log.error("OAuth login with {} failed after provider confirmed the user", provider, e);
            redirectToLogin(request, response, MessageKey.OAUTH_FAILED);
            return;
        }
        // Nửa nonce chỉ nằm trong cookie của trình duyệt này (xem OAuthLoginService.LoginCode)
        ResponseCookie nonceCookie = ResponseCookie.from(OAuthLoginService.LOGIN_NONCE_COOKIE, loginCode.nonce())
                .httpOnly(true)
                // Prod chạy sau Caddy (HTTPS): forward-headers-strategy cho isSecure() = true. Dev là http://localhost
                .secure(request.isSecure())
                .sameSite("Lax")
                .path(OAuthLoginService.LOGIN_NONCE_COOKIE_PATH)
                .maxAge(OAuthLoginService.LOGIN_CODE_TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, nonceCookie.toString());
        redirectStrategy.sendRedirect(request, response, CALLBACK_PATH + loginCode.code());
    }

    private User findOrCreateUser(String provider, ProviderProfile profile) {
        try {
            return oauthLoginService.findOrCreateUser(provider, profile.providerUserId(), profile.email(),
                    profile.displayName());
        } catch (DuplicateKeyException e) {
            return oauthLoginService.findOrCreateUser(provider, profile.providerUserId(), profile.email(),
                    profile.displayName());
        }
    }

    /** null = provider lạ (chỉ cấu hình google, github) hoặc provider không trả id */
    static ProviderProfile toProfile(String provider, OAuth2User principal) {
        ProviderProfile profile = switch (provider) {
            // Google đi luồng OIDC: các claim chuẩn sub, email, email_verified, name
            case "google" -> new ProviderProfile(
                    principal.getAttribute("sub"),
                    principal.getAttribute("email"),
                    Boolean.TRUE.equals(principal.getAttribute("email_verified")),
                    principal.getAttribute("name"));
            // GitHub: id là số; email do GitHubEmailEnricher điền, chỉ có khi vừa là email
            // chính vừa đã xác minh
            case "github" -> {
                String email = principal.getAttribute("email");
                String name = principal.getAttribute("name");
                Object id = principal.getAttribute("id");
                yield new ProviderProfile(
                        // id null mà String.valueOf thì thành chuỗi "null" → mọi lần thiếu id bị gộp vào cùng một user
                        id == null ? null : String.valueOf(id),
                        email,
                        email != null,
                        name != null && !name.isBlank() ? name : principal.getAttribute("login"));
            }
            default -> null;
        };
        // Không có id thì không biết là ai: không được tạo/liên kết tài khoản
        return profile == null || profile.providerUserId() == null ? null : profile;
    }

    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response, String messageKey)
            throws IOException {
        // Frontend đọc oauthError (là message key) và hiện toast
        redirectStrategy.sendRedirect(request, response, LOGIN_ERROR_PATH + messageKey);
    }
}
