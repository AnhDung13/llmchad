package com.llmchad.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.llmchad.exception.AppException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private record VerificationTexts(String subject, String preheader, String title, String intro, String ignore,
            String expires) {
    }

    private record ResetPasswordText(String subject, String preheader, String title, String intro, String buttonLabel,
            String fallback, String ignore,
            String expires) {
    }

    private static final VerificationTexts verificationTextsVI = new VerificationTexts(
            "Xác minh email của bạn trên LLM Chad",
            "Mã xác minh của bạn là %s, có hiệu lực trong %d phút.",
            "Mã xác minh của bạn",
            "Bạn nhận được email này vì vừa đăng ký tài khoản LLM Chad. Nhập mã bên dưới để hoàn tất.",
            "Nếu bạn không đăng ký, hãy bỏ qua email này.",
            "Mã có hiệu lực trong %d phút.");

    private static final VerificationTexts verificationTextsEN = new VerificationTexts(
            "Verify your email for LLM Chad",
            "Your verification code is %s. It expires in %d minutes.",
            "Your verification code",
            "You're receiving this email because you just signed up for LLM Chad. Enter the code below to finish.",
            "If you didn't sign up, you can safely ignore this email.",
            "The code expires in %d minutes.");

    private static final ResetPasswordText resetPasswordTextVI = new ResetPasswordText(
            "Đặt lại mật khẩu LLM Chad",
            "Bấm vào link để đặt mật khẩu mới, link có hiệu lực trong %d phút.",
            "Đặt lại mật khẩu",
            "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản LLM Chad của bạn. Bấm nút bên dưới để tạo mật khẩu mới.",
            "Đặt lại mật khẩu",
            "Nút không hoạt động? Sao chép link này vào trình duyệt:",
            "Nếu bạn không yêu cầu, hãy bỏ qua email này. Mật khẩu của bạn sẽ không thay đổi.",
            "Link có hiệu lực trong %d phút và chỉ dùng được một lần.");
    private static final ResetPasswordText resetPasswordTextEN = new ResetPasswordText(
            "Reset your LLM Chad password",
            "Use the link to set a new password. It expires in %d minutes.",
            "Reset your password",
            "We received a request to reset the password for your LLM Chad account. Click the button below to choose a new one.",
            "Reset password",
            "Button not working? Copy this link into your browser:",
            "If you didn't request this, you can ignore this email. Your password won't change.",
            "The link expires in %d minutes and can only be used once.");

    private final JavaMailSender javaMailSender;
    private final String from;
    private final String verificationTemplate;
    private final String resetPasswordTemplate;
    private final String logoUrl;

    public EmailService(JavaMailSender javaMailSender, @Value("${app.mail.from}") String from,
            @Value("${app.mail.logo-url}") String logoUrl) {
        this.javaMailSender = javaMailSender;
        this.from = from;
        this.logoUrl = logoUrl;
        this.verificationTemplate = readResource("mail/verification-code.html");
        this.resetPasswordTemplate = readResource("mail/password-reset.html");
    }

    public void sendVerificationCode(String to, String code, Duration validFor) {
        boolean english = "en".equals(LocaleContextHolder.getLocale().getLanguage());
        VerificationTexts texts = english ? verificationTextsEN : verificationTextsVI;
        String expires = texts.expires().formatted(validFor.toMinutes());

        String html = fill(verificationTemplate, Map.of(
                "lang", english ? "en" : "vi",
                "title", texts.title(),
                "preheader", texts.preheader().formatted(code, validFor.toMinutes()),
                "logoUrl", logoUrl,
                "intro", texts.intro(),
                "code", code,
                "ignore", texts.ignore(),
                "expires", expires,
                "footer", footer()));
        String plainText = "%s: %s\n\n%s %s".formatted(texts.title(), code, texts.ignore(), expires);

        send(to, texts.subject(), plainText, html, "xác minh");
    }

    public void sendPasswordResetLink(String to, String url, Duration validFor) {
        boolean english = "en".equals(LocaleContextHolder.getLocale().getLanguage());
        ResetPasswordText texts = english ? resetPasswordTextEN : resetPasswordTextVI;
        String expires = texts.expires().formatted(validFor.toMinutes());

        // Map.of chỉ nhận tối đa 10 cặp key-value, ở đây có 11
        String html = fill(resetPasswordTemplate, Map.ofEntries(
                Map.entry("lang", english ? "en" : "vi"),
                Map.entry("title", texts.title()),
                Map.entry("preheader", texts.preheader().formatted(validFor.toMinutes())),
                Map.entry("logoUrl", logoUrl),
                Map.entry("intro", texts.intro()),
                Map.entry("buttonLabel", texts.buttonLabel()),
                Map.entry("fallback", texts.fallback()),
                Map.entry("resetUrl", url),
                Map.entry("ignore", texts.ignore()),
                Map.entry("expires", expires),
                Map.entry("footer", footer())));
        String plainText = "%s: %s\n\n%s %s".formatted(texts.title(), url, texts.ignore(), expires);

        send(to, texts.subject(), plainText, html, "đặt lại mật khẩu");
    }

    private void send(String to, String subject, String plainText, String html, String purpose) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            javaMailSender.send(message);
        } catch (MailException | MessagingException e) {
            log.error("Gửi mail {} tới {} thất bại", purpose, to, e);
            throw new AppException(HttpStatus.BAD_GATEWAY, MessageKey.SEND_MAIL_FAILED);
        }
    }

    private static String footer() {
        return "© " + Year.now().getValue() + " LLM Chad";
    }

    private static String fill(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private static String readResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được " + path, e);
        }
    }
}
