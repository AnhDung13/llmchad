package com.llmchad.auth.oauth2;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.llmchad.common.MessageKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Đăng nhập Google/GitHub không thành: người dùng bấm Huỷ ở trang đồng ý,
 * `state` không khớp,
 * GitHubEmailEnricher gọi /user/emails lỗi... → về trang đăng nhập kèm thông
 * báo.
 */
@Component
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuthLoginFailureHandler.class);

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        // warn chứ không error: bấm Huỷ là chuyện bình thường
        log.warn("OAuth login failed: {}", exception.getMessage());
        // Session tạo ra để giữ `state` cho lần đăng nhập này; không huỷ thì mỗi lần bấm Huỷ (hoặc bot gọi liên tục)
        // để lại một session trên RAM tới khi hết hạn
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        redirectStrategy.sendRedirect(request, response,
                OAuthLoginSuccessHandler.LOGIN_ERROR_PATH + MessageKey.OAUTH_FAILED);
    }
}
